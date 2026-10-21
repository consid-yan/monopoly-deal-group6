package ie.ucd.bdic.group6.network.server;

import ie.ucd.bdic.group6.controller.GameController;
import ie.ucd.bdic.group6.facade.model.CommandResult;
import ie.ucd.bdic.group6.network.message.ClientRequest;
import ie.ucd.bdic.group6.network.message.Message;
import ie.ucd.bdic.group6.network.message.NoticeType;
import ie.ucd.bdic.group6.network.message.ServerNotice;
import ie.ucd.bdic.group6.network.message.ServerUpdate;

import java.io.Closeable;
import java.io.EOFException;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
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

    private final ServerRequestDispatcher dispatcher;
    private final int requestedPort;

    private final Set<ClientConnection> clients = ConcurrentHashMap.newKeySet();
    private final AtomicBoolean running = new AtomicBoolean(false);

    private ServerSocket serverSocket;
    private ScheduledExecutorService timeoutExecutor;

    public GameServer(GameController gameController, int port) {
        this.dispatcher = new ServerRequestDispatcher(gameController);
        requireValidPort(port);
        this.requestedPort = port;
    }

    public synchronized void startAsync() throws IOException {
        if (running.get()) {
            return;
        }

        serverSocket = new ServerSocket(requestedPort);
        running.set(true);

        Thread acceptThread = new Thread(this::acceptLoop, "monopoly-server-accept");
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
        ServerRequestDispatcher.DispatchResult result = dispatcher.dispatch(request);
        connection.send(result.response());

        if (result.bindConnection()) {
            bind(connection, result.sessionId(), result.playerId());
        }

        if (result.broadcastSessionState()) {
            broadcastSessionState(result.sessionId());
        }
    }

    private void bind(ClientConnection connection, String sessionId, String playerId) {
        connection.bind(sessionId, playerId);

        for (ClientConnection other : clients) {
            if (other != connection && other.isBoundToPlayer(sessionId, playerId)) {
                other.send(new ServerNotice(
                        NoticeType.SESSION_REPLACED,
                        Map.of(
                                "message", "Another connection rejoined this player session",
                                "sessionId", sessionId,
                                "playerId", playerId
                        )
                ));

                other.markReplacedAndClose();
            }
        }
    }

    /**
     * [Deep Dive: State Broadcasting — How do all clients stay in sync?]
     * After any command changes the game state, this method pushes the latest SessionSnapshot
     * to every client bound to the given session. Each client gets their own "view" of the game
     * (only their own hand cards are visible). This is a PUSH model: the server proactively sends
     * state updates via ServerUpdate messages, the client never polls.
     * Broadcasting is triggered when dispatch() returns broadcastSessionState=true.
     */
    private void broadcastSessionState(String sessionId) {
        for (ClientConnection connection : clients) {
            if (!connection.isBoundToSession(sessionId)) {
                continue;
            }

            CommandResult stateResult = dispatcher.rejoinSession(sessionId, connection.playerId());

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

        for (String sessionId : dispatcher.processDisconnectTimeouts()) {
            broadcastSessionState(sessionId);
        }
    }

    private boolean hasBoundConnection(String sessionId, String playerId) {
        for (ClientConnection connection : clients) {
            if (connection.isBoundToPlayer(sessionId, playerId)) {
                return true;
            }
        }

        return false;
    }

    private synchronized void closeServerSocket() {
        closeQuietly(serverSocket);
        serverSocket = null;
    }

    private static void closeQuietly(Closeable socket) {
        if (socket == null) {
            return;
        }

        try {
            socket.close();
        } catch (IOException ignored) {}
    }

    private void handlePlayerDisconnected(String sessionId, String playerId) {
        CommandResult result = dispatcher.playerDisconnected(sessionId, playerId);

        if (result.isSuccess()) {
            broadcastSessionState(sessionId);
        } else {
            LOGGER.warning("Failed to mark player disconnected: " + result.getMessage());
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
                    running.get() && !replaced && closedSessionId != null && closedPlayerId != null;

            close();
            clients.remove(this);

            if (shouldMarkDisconnected && !hasBoundConnection(closedSessionId, closedPlayerId)) {
                handlePlayerDisconnected(closedSessionId, closedPlayerId);
            }

            LOGGER.fine("Connection cleanup finished for " + clientAddress());
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

        private void markReplacedAndClose() {
            replaced = true;
            close();
        }

        private boolean isBoundToSession(String sessionId) {
            return open.get()
                    && Objects.equals(this.sessionId, sessionId)
                    && playerId != null;
        }

        private boolean isBoundToPlayer(String sessionId, String playerId) {
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
