package ie.ucd.bdic.group6.network.server;

import ie.ucd.bdic.group6.command.CommandType;
import ie.ucd.bdic.group6.controller.GameController;
import ie.ucd.bdic.group6.controller.dto.ControllerRequest;
import ie.ucd.bdic.group6.facade.model.GameResult;
import ie.ucd.bdic.group6.network.protocol.ClientCommand;
import ie.ucd.bdic.group6.network.protocol.ServerNotice;
import ie.ucd.bdic.group6.network.protocol.ServerResult;
import ie.ucd.bdic.group6.network.protocol.StateUpdate;

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

public class GameServer implements AutoCloseable {
    private final GameController gameController;
    private final int requestedPort;
    private final Set<ClientConnection> clients = ConcurrentHashMap.newKeySet();
    private final Map<String, Map<String, String>> playerTokensBySession = new ConcurrentHashMap<>();
    private final Map<String, String> sessionIdByCode = new ConcurrentHashMap<>();
    private final AtomicBoolean running = new AtomicBoolean(false);

    private ServerSocket serverSocket;
    private Thread acceptThread;
    private ScheduledExecutorService timeoutExecutor;

    public GameServer(GameController gameController, int port) {
        this.gameController = Objects.requireNonNull(gameController, "gameController");
        if (port < 0 || port > 65535) {
            throw new IllegalArgumentException("port must be between 0 and 65535");
        }
        this.requestedPort = port;
    }

    public synchronized void startAsync() throws IOException {
        if (running.get()) {
            return;
        }
        serverSocket = new ServerSocket(requestedPort);
        running.set(true);
        acceptThread = new Thread(this::acceptLoop, "MonopolyDealServer-Accept");
        acceptThread.setDaemon(true);
        acceptThread.start();
        timeoutExecutor = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "MonopolyDealServer-DisconnectTimeouts");
            thread.setDaemon(true);
            return thread;
        });
        timeoutExecutor.scheduleAtFixedRate(this::processDisconnectTimeouts, 1, 1, TimeUnit.SECONDS);
    }

    public int port() {
        ServerSocket currentSocket = serverSocket;
        if (currentSocket == null) {
            return requestedPort;
        }
        return currentSocket.getLocalPort();
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
                connection.send(new ServerNotice("CONNECTED", "Connected to Monopoly Deal server",
                        Map.of("port", port())));
            } catch (SocketException e) {
                if (running.get()) {
                    running.set(false);
                }
            } catch (IOException e) {
                if (running.get()) {
                    // Keep accepting future connections after a single bad socket.
                }
            }
        }
    }

    private void handleCommand(ClientConnection connection, ClientCommand command) {
        ServerResult authFailure = authenticate(command);
        if (authFailure != null) {
            connection.send(authFailure);
            return;
        }

        GameResult result = execute(command);
        String sessionId = text(result.getData().get("sessionId"));
        String playerId = resolvePlayerId(command);
        Map<String, Object> resultData = result.getData();
        if (result.isSuccess()) {
            rememberSessionCode(resultData);
            resultData = withIssuedPlayerToken(command, sessionId, playerId, resultData);
        }
        ServerResult serverResult = new ServerResult(
                command.requestId(),
                result.isSuccess(),
                result.getCode(),
                result.getMessage(),
                resultData);
        connection.send(serverResult);

        if (!result.isSuccess()) {
            return;
        }

        if (sessionId != null && playerId != null) {
            bind(connection, sessionId, playerId);
            broadcastSessionState(sessionId);
        }
    }

    private ServerResult authenticate(ClientCommand command) {
        if (command.commandType() == CommandType.CREATE_SESSION
                || command.commandType() == CommandType.JOIN_SESSION) {
            return null;
        }

        String sessionId = authenticatedSessionId(command);
        String playerId = text(command.payload().get("playerId"));
        String playerToken = text(command.payload().get("playerToken"));
        if (sessionId == null || playerId == null || playerToken == null) {
            return new ServerResult(command.requestId(), false, "AUTH_REQUIRED",
                    "A valid player token is required for " + command.commandType(), Map.of());
        }

        String expectedToken = playerTokensBySession
                .getOrDefault(sessionId, Map.of())
                .get(playerId);
        if (!playerToken.equals(expectedToken)) {
            return new ServerResult(command.requestId(), false, "UNAUTHORIZED_PLAYER",
                    "Player identity could not be verified", Map.of());
        }
        return null;
    }

    private String authenticatedSessionId(ClientCommand command) {
        if (command.commandType() == CommandType.REJOIN_SESSION) {
            String sessionIdentity = text(command.payload().get("sessionIdentity"));
            if (sessionIdentity == null) {
                return null;
            }
            return sessionIdByCode.getOrDefault(sessionIdentity, sessionIdentity);
        }
        return text(command.payload().get("sessionId"));
    }

    private void rememberSessionCode(Map<String, Object> data) {
        String sessionId = text(data.get("sessionId"));
        String sessionCode = text(data.get("sessionCode"));
        if (sessionId != null && sessionCode != null) {
            sessionIdByCode.put(sessionCode, sessionId);
        }
    }

    private Map<String, Object> withIssuedPlayerToken(
            ClientCommand command,
            String sessionId,
            String playerId,
            Map<String, Object> data) {
        if (sessionId == null || playerId == null) {
            return data;
        }

        String playerToken = switch (command.commandType()) {
            case CREATE_SESSION, JOIN_SESSION -> issuePlayerToken(sessionId, playerId);
            case REJOIN_SESSION -> playerTokensBySession.getOrDefault(sessionId, Map.of()).get(playerId);
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
        Map<String, String> tokens = playerTokensBySession.computeIfAbsent(
                sessionId,
                _ -> new ConcurrentHashMap<>());
        return tokens.computeIfAbsent(playerId, _ -> UUID.randomUUID().toString());
    }

    private GameResult execute(ClientCommand command) {
        try {
            ControllerRequest request = new ControllerRequest(command.payload());
            return switch (command.commandType()) {
                case CREATE_SESSION -> gameController.createSession(request);
                case JOIN_SESSION -> gameController.joinSession(request);
                case START_GAME -> gameController.startGame(request);
                case PLAY_CARD -> gameController.playCard(request);
                case PAY_DEBT -> gameController.payDebt(request);
                case PROPOSE_TRADE -> gameController.proposeTrade(request);
                case RESPOND_TRADE -> gameController.respondTrade(request);
                case RESPOND_ACTION -> gameController.respondAction(request);
                case DISCARD_CARDS -> gameController.discardCards(request);
                case END_TURN -> gameController.endTurn(request);
                case REJOIN_SESSION -> gameController.rejoinSession(request);
                case PLAYER_DISCONNECTED -> gameController.playerDisconnected(request);
                case PROCESS_DISCONNECT_TIMEOUTS -> gameController.processDisconnectTimeouts(request);
            };
        } catch (RuntimeException e) {
            return GameResult.failure("NETWORK_COMMAND_FAILED",
                    "Failed to execute " + command.commandType() + ": " + e.getMessage());
        }
    }

    private void bind(ClientConnection connection, String sessionId, String playerId) {
        connection.bind(sessionId, playerId);
        for (ClientConnection other : clients) {
            if (other != connection && other.isBoundTo(sessionId, playerId)) {
                other.send(new ServerNotice("SESSION_REPLACED",
                        "Another connection rejoined this player session", Map.of(
                        "sessionId", sessionId,
                        "playerId", playerId)));
                other.replaceAndClose();
            }
        }
    }

    private void broadcastSessionState(String sessionId) {
        for (ClientConnection connection : clients) {
            if (!connection.isBoundTo(sessionId)) {
                continue;
            }
            GameResult snapshot = gameController.rejoinSession(new ControllerRequest(Map.of(
                    "sessionIdentity", sessionId,
                    "playerId", connection.playerId())));
            if (snapshot.isSuccess()) {
                connection.send(new StateUpdate(sessionId, connection.playerId(), snapshot.getData()));
            } else {
                connection.send(new ServerNotice("STATE_UPDATE_FAILED", snapshot.getMessage(), Map.of(
                        "code", snapshot.getCode(),
                        "sessionId", sessionId,
                        "playerId", connection.playerId())));
            }
        }
    }

    @SuppressWarnings("unchecked")
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

    private String resolvePlayerId(ClientCommand command) {
        String playerId = text(command.payload().get("playerId"));
        if (playerId != null) {
            return playerId;
        }
        if (command.commandType() == CommandType.CREATE_SESSION) {
            return text(command.payload().get("hostPlayerId"));
        }
        return null;
    }

    private String text(Object value) {
        if (value == null) {
            return null;
        }
        String text = value.toString().trim();
        return text.isEmpty() ? null : text;
    }

    private void closeServerSocket() {
        if (serverSocket == null) {
            return;
        }
        try {
            serverSocket.close();
        } catch (IOException ignored) {
            // Best-effort shutdown.
        }
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
            this.thread = new Thread(this, "MonopolyDealServer-Client-" + socket.getPort());
            this.thread.setDaemon(true);
        }

        private void start() {
            thread.start();
        }

        @Override
        public void run() {
            try {
                while (running.get() && open.get()) {
                    Object message = input.readObject();
                    if (message instanceof ClientCommand command) {
                        handleCommand(this, command);
                    } else {
                        send(new ServerNotice("INVALID_MESSAGE",
                                "Server expected a ClientCommand", Map.of()));
                    }
                }
            } catch (EOFException | SocketException ignored) {
                // Normal client disconnect.
            } catch (IOException | ClassNotFoundException e) {
                if (open.get()) {
                    send(new ServerNotice("CONNECTION_ERROR", e.getMessage(), Map.of()));
                }
            } finally {
                String closedSessionId = sessionId;
                String closedPlayerId = playerId;
                boolean shouldMarkDisconnected = running.get()
                        && !replaced
                        && closedSessionId != null
                        && closedPlayerId != null;
                close();
                clients.remove(this);
                if (shouldMarkDisconnected && hasBoundConnection(closedSessionId, closedPlayerId)) {
                    shouldMarkDisconnected = false;
                }
                if (shouldMarkDisconnected) {
                    GameResult result = gameController.playerDisconnected(new ControllerRequest(Map.of(
                            "sessionId", closedSessionId,
                            "playerId", closedPlayerId
                    )));
                    if (result.isSuccess()) {
                        broadcastSessionState(closedSessionId);
                    }
                }
            }
        }

        private boolean hasBoundConnection(String sessionId, String playerId) {
            for (ClientConnection connection : clients) {
                if (connection.isBoundTo(sessionId, playerId)) {
                    return true;
                }
            }
            return false;
        }

        private synchronized void send(Object message) {
            if (!open.get()) {
                return;
            }
            try {
                output.writeObject(message);
                output.flush();
                output.reset();
            } catch (IOException e) {
                close();
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
            return open.get() && Objects.equals(this.sessionId, sessionId) && playerId != null;
        }

        private boolean isBoundTo(String sessionId, String playerId) {
            return open.get()
                    && Objects.equals(this.sessionId, sessionId)
                    && Objects.equals(this.playerId, playerId);
        }

        private String playerId() {
            return playerId;
        }

        @Override
        public void close() {
            if (!open.getAndSet(false)) {
                return;
            }
            try {
                socket.close();
            } catch (IOException ignored) {
                // Best-effort shutdown.
            }
        }
    }
}
