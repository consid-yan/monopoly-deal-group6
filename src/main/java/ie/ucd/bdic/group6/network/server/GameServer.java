package ie.ucd.bdic.group6.network.server;

import ie.ucd.bdic.group6.command.CommandType;
import ie.ucd.bdic.group6.controller.GameController;
import ie.ucd.bdic.group6.controller.dto.ControllerRequest;
import ie.ucd.bdic.group6.core.snapshot.SessionSnapshot;
import ie.ucd.bdic.group6.facade.model.GameResult;
import ie.ucd.bdic.group6.network.message.ClientRequest;
import ie.ucd.bdic.group6.network.message.Message;
import ie.ucd.bdic.group6.network.message.NoticeType;
import ie.ucd.bdic.group6.network.message.ServerNotice;
import ie.ucd.bdic.group6.network.message.ServerResponse;
import ie.ucd.bdic.group6.network.message.ServerUpdate;

import java.io.EOFException;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

import static ie.ucd.bdic.group6.network.NetworkConfig.requireValidPort;

public class GameServer implements AutoCloseable {
    private static final Logger LOGGER = Logger.getLogger(GameServer.class.getName());

    private final GameController gameController;
    private final int requestedPort;

    private final Set<ClientConnection> clients = ConcurrentHashMap.newKeySet();
    private final Map<String, Map<String, String>> playerTokensBySessionId = new ConcurrentHashMap<>();
    private final Map<String, String> sessionIdByRoomCode = new ConcurrentHashMap<>();
    private final AtomicBoolean running = new AtomicBoolean(false);

    private ServerSocket serverSocket;
    private Thread acceptThread;
    private ScheduledExecutorService timeoutExecutor;

    public GameServer(GameController gameController, int port) {
        this.gameController = Objects.requireNonNull(gameController, "gameController");
        requireValidPort(port);
        this.requestedPort = port;
    }

    public synchronized void startAsync() throws IOException {
        if (running.get()) {
            return;
        }

        serverSocket = new ServerSocket(requestedPort);
        running.set(true);

        acceptThread = new Thread(this::acceptLoop, "monopoly-server-accept");
        acceptThread.setDaemon(true);
        acceptThread.start();

        timeoutExecutor = Executors.newSingleThreadScheduledExecutor(task -> {
            Thread thread = new Thread(task, "monopoly-server-disconnect-timeouts");
            thread.setDaemon(true);
            return thread;
        });

        timeoutExecutor.scheduleAtFixedRate(this::processDisconnectTimeouts, 1, 1, TimeUnit.SECONDS);

        LOGGER.info("Server started on port " + port());
    }

    public int port() {
        ServerSocket currentSocket = serverSocket;
        return currentSocket == null ? requestedPort : currentSocket.getLocalPort();
    }

    public boolean isRunning() {
        return running.get();
    }

    public void stop() {
        if (!running.getAndSet(false)) {
            return;
        }

        closeServerSocket();

        if (timeoutExecutor != null) {
            timeoutExecutor.shutdownNow();
            timeoutExecutor = null;
        }

        clients.forEach(ClientConnection::close);
        clients.clear();

        LOGGER.info("Server stopped");
    }

    @Override
    public void close() {
        stop();
    }

    private void acceptLoop() {
        while (running.get()) {
            try {
                Socket socket = serverSocket.accept();
                ClientConnection connection = new ClientConnection(socket);

                clients.add(connection);
                connection.start();

                connection.send(new ServerNotice(
                        NoticeType.CONNECTED,
                        Map.of(
                                "message", "Connected to Monopoly Deal server",
                                "port", port()
                        )
                ));
            } catch (SocketException e) {
                if (running.get()) {
                    LOGGER.log(Level.WARNING, "Server socket closed unexpectedly", e);
                    running.set(false);
                }
            } catch (IOException e) {
                if (running.get()) {
                    LOGGER.log(Level.WARNING, "Failed to accept client connection", e);
                }
            }
        }
    }

    private void handleRequest(ClientConnection connection, ClientRequest request) {
        ServerResponse authFailure = authenticate(request);

        if (authFailure != null) {
            connection.send(authFailure);
            return;
        }

        GameResult result = execute(request);

        SessionSnapshot snapshot = result.getSnapshot().orElse(null);
        String sessionId = snapshot == null ? text(result.getData().get("sessionId")) : text(snapshot.sessionId());
        String playerId = resolvePlayerId(request);
        Map<String, Object> resultData = result.getData();

        if (result.isSuccess()) {
            if (snapshot != null) {
                rememberRoomCode(snapshot);
                resultData = responsePayload(snapshot);
            }
            resultData = withIssuedPlayerToken(request, sessionId, playerId, resultData);
        }

        connection.send(new ServerResponse(
                request.requestId(),
                result.isSuccess(),
                result.getCode(),
                result.getMessage(),
                resultData,
                snapshot
        ));

        if (!result.isSuccess()) {
            return;
        }

        if (sessionId != null && playerId != null) {
            bind(connection, sessionId, playerId);
            broadcastSessionState(sessionId);
        }
    }

    private ServerResponse authenticate(ClientRequest request) {
        CommandType commandType = request.commandType();

        if (commandType == CommandType.CREATE_SESSION || commandType == CommandType.JOIN_SESSION) {
            return null;
        }

        String sessionId = authenticatedSessionId(request);
        String playerId = text(request.payload().get("playerId"));
        String playerToken = text(request.payload().get("playerToken"));

        if (sessionId == null || playerId == null || playerToken == null) {
            return new ServerResponse(
                    request.requestId(),
                    false,
                    "AUTH_REQUIRED",
                    "A valid player token is required for " + commandType,
                    Map.of(), null
            );
        }

        String expectedToken = playerTokensBySessionId
                .getOrDefault(sessionId, Map.of())
                .get(playerId);

        if (!playerToken.equals(expectedToken)) {
            return new ServerResponse(
                    request.requestId(),
                    false,
                    "UNAUTHORIZED_PLAYER",
                    "Player identity could not be verified",
                    Map.of(), null
            );
        }

        return null;
    }

    private String authenticatedSessionId(ClientRequest request) {
        if (request.commandType() == CommandType.REJOIN_SESSION) {
            String sessionIdentity = text(request.payload().get("sessionIdentity"));

            if (sessionIdentity == null) {
                return null;
            }

            return sessionIdByRoomCode.getOrDefault(sessionIdentity, sessionIdentity);
        }

        return text(request.payload().get("sessionId"));
    }

    private GameResult execute(ClientRequest request) {
        try {
            ControllerRequest controllerRequest = new ControllerRequest(request.payload());

            return switch (request.commandType()) {
                case CREATE_SESSION -> gameController.createSession(controllerRequest);
                case JOIN_SESSION -> gameController.joinSession(controllerRequest);
                case START_GAME -> gameController.startGame(controllerRequest);
                case PLAY_CARD -> gameController.playCard(controllerRequest);
                case PAY_DEBT -> gameController.payDebt(controllerRequest);
                case PROPOSE_TRADE -> gameController.proposeTrade(controllerRequest);
                case RESPOND_TRADE -> gameController.respondTrade(controllerRequest);
                case RESPOND_ACTION -> gameController.respondAction(controllerRequest);
                case DISCARD_CARDS -> gameController.discardCards(controllerRequest);
                case END_TURN -> gameController.endTurn(controllerRequest);
                case REJOIN_SESSION -> gameController.rejoinSession(controllerRequest);
                case PLAYER_DISCONNECTED -> gameController.playerDisconnected(controllerRequest);
                case PROCESS_DISCONNECT_TIMEOUTS -> gameController.processDisconnectTimeouts(controllerRequest);
            };
        } catch (RuntimeException e) {
            LOGGER.log(Level.WARNING, "Failed to execute command " + request.commandType(), e);

            return GameResult.failure(
                    "NETWORK_COMMAND_FAILED",
                    "Failed to execute " + request.commandType() + ": " + e.getMessage()
            );
        }
    }

    private void rememberRoomCode(SessionSnapshot snapshot) {
        String sessionId = text(snapshot.sessionId());
        String roomCode = text(snapshot.roomCode());

        if (sessionId != null && roomCode != null) {
            sessionIdByRoomCode.put(roomCode, sessionId);
        }
    }

    private Map<String, Object> responsePayload(SessionSnapshot snapshot) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("sessionId", snapshot.sessionId());
        payload.put("roomCode", snapshot.roomCode());
        return payload;
    }

    private Map<String, Object> withIssuedPlayerToken(
            ClientRequest request,
            String sessionId,
            String playerId,
            Map<String, Object> data
    ) {
        if (sessionId == null || playerId == null) {
            return data;
        }

        String playerToken = switch (request.commandType()) {
            case CREATE_SESSION, JOIN_SESSION -> issuePlayerToken(sessionId, playerId);
            case REJOIN_SESSION -> playerTokensBySessionId
                    .getOrDefault(sessionId, Map.of())
                    .get(playerId);
            default -> null;
        };

        if (playerToken == null) {
            return data;
        }

        Map<String, Object> enriched = new LinkedHashMap<>(data);
        enriched.put("playerToken", playerToken);
        return enriched;
    }

    private String issuePlayerToken(String sessionId, String playerId) {
        Map<String, String> tokens = playerTokensBySessionId.computeIfAbsent(
                sessionId,
                ignored -> new ConcurrentHashMap<>()
        );

        return tokens.computeIfAbsent(playerId, ignored -> UUID.randomUUID().toString());
    }

    private void bind(ClientConnection connection, String sessionId, String playerId) {
        connection.bind(sessionId, playerId);

        for (ClientConnection other : clients) {
            if (other != connection && other.isBoundTo(sessionId, playerId)) {
                other.send(new ServerNotice(
                        NoticeType.SESSION_REPLACED,
                        Map.of(
                                "message", "Another connection rejoined this player session",
                                "sessionId", sessionId,
                                "playerId", playerId
                        )
                ));

                other.replaceAndClose();
            }
        }
    }

    private void broadcastSessionState(String sessionId) {
        for (ClientConnection connection : clients) {
            if (!connection.isBoundTo(sessionId)) {
                continue;
            }

            GameResult stateResult = gameController.rejoinSession(new ControllerRequest(Map.of(
                    "sessionIdentity", sessionId,
                    "playerId", connection.playerId()
            )));

            if (stateResult.isSuccess() && stateResult.getSnapshot().isPresent()) {
                connection.send(new ServerUpdate(stateResult.getSnapshot().orElseThrow()));
            } else {
                connection.send(new ServerNotice(
                        NoticeType.STATE_UPDATE_FAILED,
                        Map.of(
                                "message", stateResult.getMessage(),
                                "code", stateResult.getCode(),
                                "sessionId", sessionId,
                                "playerId", connection.playerId()
                        )
                ));
            }
        }
    }

    private void processDisconnectTimeouts() {
        if (!running.get()) {
            return;
        }

        GameResult result = gameController.processDisconnectTimeouts(new ControllerRequest(Map.of()));

        if (!result.isSuccess()) {
            return;
        }

        Object rawSessionIds = result.getData().get("sessionIds");

        if (!(rawSessionIds instanceof List<?> sessionIds)) {
            return;
        }

        for (Object sessionId : sessionIds) {
            if (sessionId != null) {
                broadcastSessionState(sessionId.toString());
            }
        }
    }

    private String resolvePlayerId(ClientRequest request) {
        String playerId = text(request.payload().get("playerId"));

        if (playerId != null) {
            return playerId;
        }

        if (request.commandType() == CommandType.CREATE_SESSION) {
            return text(request.payload().get("hostPlayerId"));
        }

        return null;
    }

    private boolean hasBoundConnection(String sessionId, String playerId) {
        for (ClientConnection connection : clients) {
            if (connection.isBoundTo(sessionId, playerId)) {
                return true;
            }
        }

        return false;
    }

    private static String text(Object value) {
        if (value == null) {
            return null;
        }

        String text = value.toString().trim();
        return text.isEmpty() ? null : text;
    }

    private synchronized void closeServerSocket() {
        closeQuietly(serverSocket);
        serverSocket = null;
    }

    private static void closeQuietly(ServerSocket socket) {
        if (socket == null) {
            return;
        }

        try {
            socket.close();
        } catch (IOException ignored) {}
    }

    private static void closeQuietly(Socket socket) {
        if (socket == null) {
            return;
        }

        try {
            socket.close();
        } catch (IOException ignored) {}
    }

    private final class ClientConnection implements Runnable, AutoCloseable {
        private final Socket socket;
        private final ObjectOutputStream output;
        private final ObjectInputStream input;
        private final AtomicBoolean open = new AtomicBoolean(true);
        private final Thread thread;

        private volatile String sessionId;
        private volatile String playerId;
        private volatile boolean replaced;

        private ClientConnection(Socket socket) throws IOException {
            this.socket = Objects.requireNonNull(socket, "socket");

            this.output = new ObjectOutputStream(socket.getOutputStream());
            this.output.flush();

            this.input = new ObjectInputStream(socket.getInputStream());

            this.thread = new Thread(this, "monopoly-server-client-" + socket.getPort());
            this.thread.setDaemon(true);
        }

        private void start() {
            thread.start();
        }

        @Override
        public void run() {
            try {
                while (running.get() && open.get()) {
                    Object incomingObject = input.readObject();

                    if (incomingObject instanceof ClientRequest request) {
                        handleRequest(this, request);
                    } else {
                        handleInvalidMessage(incomingObject);
                    }
                }
            } catch (EOFException e) {
                LOGGER.info("Client disconnected normally: " + clientAddress());
            } catch (SocketException e) {
                if (open.get()) {
                    LOGGER.log(Level.INFO, "Client socket closed: " + clientAddress(), e);
                }
            } catch (IOException | ClassNotFoundException e) {
                LOGGER.log(Level.WARNING, "Client communication failed: " + clientAddress(), e);

                if (open.get()) {
                    send(new ServerNotice(
                            NoticeType.CONNECTION_ERROR,
                            Map.of("message", e.getMessage() == null ? "" : e.getMessage())
                    ));
                }
            } finally {
                cleanupAfterClose();
            }
        }

        private void handleInvalidMessage(Object incomingObject) {
            String messageType = incomingObject == null ? "null" : incomingObject.getClass().getName();

            LOGGER.warning("Invalid client message type: " + messageType);

            send(new ServerNotice(
                    NoticeType.INVALID_MESSAGE,
                    Map.of(
                            "message", "Server expected a ClientRequest",
                            "messageType", messageType
                    )
            ));
        }

        private void cleanupAfterClose() {
            String closedSessionId = sessionId;
            String closedPlayerId = playerId;

            boolean shouldMarkDisconnected =
                    running.get()
                            && !replaced
                            && closedSessionId != null
                            && closedPlayerId != null;

            close();
            clients.remove(this);

            if (shouldMarkDisconnected && hasBoundConnection(closedSessionId, closedPlayerId)) {
                shouldMarkDisconnected = false;
            }

            if (shouldMarkDisconnected) {
                markPlayerDisconnected(closedSessionId, closedPlayerId);
            }

            LOGGER.fine("Connection cleanup finished for " + clientAddress());
        }

        private void markPlayerDisconnected(String sessionId, String playerId) {
            GameResult result = gameController.playerDisconnected(new ControllerRequest(Map.of(
                    "sessionId", sessionId,
                    "playerId", playerId
            )));

            if (result.isSuccess()) {
                broadcastSessionState(sessionId);
            } else {
                LOGGER.warning("Failed to mark player disconnected: " + result.getMessage());
            }
        }

        private synchronized void send(Message message) {
            if (!open.get()) {
                return;
            }

            try {
                output.writeObject(message);
                output.flush();
                output.reset();
            } catch (SocketException e) {
                open.set(false);
                closeQuietly(socket);
            } catch (IOException e) {
                LOGGER.log(Level.WARNING, "Failed to send message to client: " + clientAddress(), e);
            }
        }

        private void bind(String sessionId, String playerId) {
            this.sessionId = sessionId;
            this.playerId = playerId;
        }

        private void replaceAndClose() {
            replaced = true;
            close();
        }

        private boolean isBoundTo(String sessionId) {
            return open.get()
                    && Objects.equals(this.sessionId, sessionId)
                    && playerId != null;
        }

        private boolean isBoundTo(String sessionId, String playerId) {
            return open.get()
                    && Objects.equals(this.sessionId, sessionId)
                    && Objects.equals(this.playerId, playerId);
        }

        private String playerId() {
            return playerId;
        }

        private String clientAddress() {
            return socket.getInetAddress().getHostAddress() + ":" + socket.getPort();
        }

        @Override
        public void close() {
            if (!open.getAndSet(false)) {
                return;
            }

            closeQuietly(socket);
        }
    }
}
