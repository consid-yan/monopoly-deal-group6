package ie.ucd.bdic.group6.network.client;

import ie.ucd.bdic.group6.command.CommandType;
import ie.ucd.bdic.group6.network.message.*;

import java.io.*;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

import static ie.ucd.bdic.group6.network.NetworkConfig.CONNECT_TIMEOUT_MS;
import static ie.ucd.bdic.group6.network.NetworkConfig.requireValidPort;

public class GameClient implements AutoCloseable {
    private static final Logger LOGGER = Logger.getLogger(GameClient.class.getName());

    private final Map<String, CompletableFuture<ServerResponse>> pendingRequests = new ConcurrentHashMap<>();
    private final CopyOnWriteArrayList<Consumer<ServerUpdate>> updateListeners = new CopyOnWriteArrayList<>();
    private final CopyOnWriteArrayList<Consumer<ServerNotice>> noticeListeners = new CopyOnWriteArrayList<>();
    private final CopyOnWriteArrayList<Consumer<Throwable>> disconnectListeners = new CopyOnWriteArrayList<>();

    private ObjectOutputStream output;

    private Socket socket;
    private final AtomicBoolean connected = new AtomicBoolean(false);

    public synchronized void connect(String serverAddress, int port) throws IOException {
        Objects.requireNonNull(serverAddress, "serverAddress must be non-null");
        requireValidPort(port);

        if (connected.get()) {
            LOGGER.warning("Client is already connected; duplicate connection ignored");
            return;
        }

        Socket candidateSocket = new Socket();
        try {
            candidateSocket.connect(new InetSocketAddress(serverAddress, port), CONNECT_TIMEOUT_MS);

            ObjectOutputStream out = new ObjectOutputStream(candidateSocket.getOutputStream());
            out.flush();

            ObjectInputStream in = new ObjectInputStream(candidateSocket.getInputStream());

            socket = candidateSocket;
            output = out;
            connected.set(true);

            Thread receiverThread = new Thread(new ClientReceiver(in, this), "client-receiver");
            receiverThread.setDaemon(true);
            receiverThread.start();

            LOGGER.info("Connected to server " + serverAddress + ":" + port);
        } catch (IOException e) {
            closeSocket(candidateSocket);
            LOGGER.log(Level.SEVERE, "Failed to connect to server " + serverAddress + ":" + port, e);

            throw e;
        }
    }

    public boolean isConnected() {
        return connected.get();
    }

    private synchronized void send(ClientRequest request) throws IOException {
        if (output == null) {
            throw new IOException("Output stream is not available");
        }

        output.writeObject(request);
        output.flush();
        output.reset();
    }

    /**
     * [Deep Dive: Async Request-Response Matching — How does the client match responses to requests?]
     * Each request gets a random UUID (requestId). A CompletableFuture is stored in a
     * ConcurrentHashMap keyed by that UUID. The request is serialized and sent over TCP.
     * Later, when ClientReceiver reads a ServerResponse from the socket, it looks up the
     * matching future by response.requestId() and completes it. If the response never arrives
     * (disconnect), all pending futures are completed exceptionally with a CancellationException.
     * This is a simple but effective async RPC pattern over raw TCP sockets.
     */
    public CompletableFuture<ServerResponse> sendCommand(CommandType commandType,
                                                         Map<String, Object> payload) {
        Objects.requireNonNull(commandType, "commandType");

        if (!connected.get()) {
            return CompletableFuture.failedFuture(new IllegalStateException("Client is not connected"));
        }

        // Creates the content of the request
        String requestId = UUID.randomUUID().toString();
        CompletableFuture<ServerResponse> future = new CompletableFuture<>();

        pendingRequests.put(requestId, future);
        try {
            send(new ClientRequest(requestId, commandType, payload));
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Failed to send command " + commandType, e);
            handleDisconnect(e);
        }

        return future;
    }

    public void addUpdateListener(Consumer<ServerUpdate> listener) {
        updateListeners.add(Objects.requireNonNull(listener, "listener"));
    }

    public void addNoticeListener(Consumer<ServerNotice> listener) {
        noticeListeners.add(Objects.requireNonNull(listener, "listener"));
    }

    public void addDisconnectListener(Consumer<Throwable> listener) {
        disconnectListeners.add(Objects.requireNonNull(listener, "listener"));
    }

    void handleMessage(Object inputObject) {
        if (!(inputObject instanceof Message message)) {
            LOGGER.warning("Received unknown message type: "
                    + (inputObject == null ? "null" : inputObject.getClass().getName()));
            return;
        }

        switch (message) {
            case ServerResponse response -> {
                CompletableFuture<ServerResponse> future = pendingRequests.remove(response.requestId());

                if (future != null) {
                    future.complete(response);
                } else {
                    LOGGER.warning("Received response with no pending request: " + response.requestId());
                }
                return;
            }
            case ServerUpdate update -> {
                notifyListeners(updateListeners, update);
                return;
            }
            case ServerNotice notice -> {
                notifyListeners(noticeListeners, notice);
                return;
            }
            default -> {}
        }

        LOGGER.warning("Unhandled message type: " + message.getClass().getName());
    }

    void handleDisconnect(Exception cause) {
        // Someone had handled it
        if (!connected.getAndSet(false)) {
            return;
        }

        // Handles it
        CancellationException cancellation = new CancellationException("Client disconnected");

        if (cause != null) {
            cancellation.initCause(cause);
        }

        if (cause == null) {
            LOGGER.info("Disconnected from server normally");
        } else {
            LOGGER.log(Level.WARNING, "Disconnected from server", cause);
        }

        closeConnection();
        notifyListeners(disconnectListeners, cancellation);
        failPendingRequests(cancellation);
    }

    private <T> void notifyListeners(CopyOnWriteArrayList<Consumer<T>> listeners, T event) {
        for (Consumer<T> listener : listeners) {
            try {
                listener.accept(event);
            } catch (RuntimeException e) {
                LOGGER.log(Level.SEVERE, "Listener failed while handling event", e);
            }
        }
    }

    @Override
    public void close() {
        if (connected.getAndSet(false)) {
            CancellationException cancellation = new CancellationException("Client closed");

            failPendingRequests(cancellation);
        }

        closeConnection();
    }

    private void failPendingRequests(Throwable cause) {
        pendingRequests.values().forEach(future ->
                future.completeExceptionally(cause));
        pendingRequests.clear();
    }

    private synchronized void closeConnection() {
        closeSocket(socket);
        socket = null;
        output = null;
    }

    private void closeSocket(Socket socket) {
        if (socket == null) {
            return;
        }
        try {
            socket.close();
        } catch (IOException ignored) {}
    }
}
