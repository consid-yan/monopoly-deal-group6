package ie.ucd.bdic.group6.network.client;

import ie.ucd.bdic.group6.command.CommandType;
import ie.ucd.bdic.group6.network.protocol.ClientCommand;
import ie.ucd.bdic.group6.network.protocol.ServerNotice;
import ie.ucd.bdic.group6.network.protocol.ServerResult;
import ie.ucd.bdic.group6.network.protocol.StateUpdate;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
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

public class GameClient implements AutoCloseable {
    private final Map<String, CompletableFuture<ServerResult>> pendingResults = new ConcurrentHashMap<>();
    private final CopyOnWriteArrayList<Consumer<ServerResult>> resultListeners = new CopyOnWriteArrayList<>();
    private final CopyOnWriteArrayList<Consumer<StateUpdate>> stateUpdateListeners = new CopyOnWriteArrayList<>();
    private final CopyOnWriteArrayList<Consumer<ServerNotice>> noticeListeners = new CopyOnWriteArrayList<>();
    private final AtomicBoolean connected = new AtomicBoolean(false);

    private Socket socket;
    private ObjectOutputStream output;

    public synchronized void connect(String host, int port) throws IOException {
        System.out.println("[Client] Connecting to server: " + host + ":" + port);

        if (connected.get()) {
            System.out.println("[Client] ⚠️  Client already connected, skip duplicate connection");
            return;
        }

        try {
            socket = new Socket(Objects.requireNonNull(host, "host"), port);
            System.out.println("[Client] ✅ TCP connection established successfully");

            output = new ObjectOutputStream(socket.getOutputStream());
            output.flush();
            System.out.println("[Client] ✅ Output stream initialized");

            var input = new ObjectInputStream(socket.getInputStream());
            System.out.println("[Client] ✅ Input stream initialized");

            connected.set(true);
            var gameThread = new GameThread(input, this);
            gameThread.start();
            System.out.println("[Client] ✅ Message reading thread started");

        } catch (IOException e) {
            System.err.println("[Client] ❌ Failed to connect to server");
            System.err.println("   Target address: " + host + ":" + port);
            System.err.println("   Error type: " + e.getClass().getSimpleName());
            System.err.println("   Error message: " + e.getMessage());

            if (e.getMessage().contains("Connection refused")) {
                System.err.println("   🔍 Troubleshoot: Server not started, wrong port, inbound connection blocked by firewall");
            } else if (e.getMessage().contains("Connection timed out")) {
                System.err.println("   🔍 Troubleshoot: Network unreachable, incorrect IP, outbound connection blocked by firewall");
            } else if (e.getMessage().contains("No route to host")) {
                System.err.println("   🔍 Troubleshoot: Server offline, not in same LAN, invalid IP address");
            }

            closeSocket();
            throw e;
        }
    }

    public boolean isConnected() {
        return connected.get();
    }

    public CompletableFuture<ServerResult> sendCommand(CommandType commandType, Map<String, Object> payload) {
        Objects.requireNonNull(commandType, "commandType");
        System.out.println("[Client] Sending command: " + commandType);

        if (!connected.get()) {
            CompletableFuture<ServerResult> failed = new CompletableFuture<>();
            failed.completeExceptionally(new IllegalStateException("Client is not connected"));
            System.err.println("[Client] ❌ Failed to send command: Client is offline");
            return failed;
        }

        String requestId = UUID.randomUUID().toString();
        CompletableFuture<ServerResult> future = new CompletableFuture<>();
        pendingResults.put(requestId, future);

        try {
            send(new ClientCommand(requestId, commandType, payload));
            System.out.println("[Client] ✅ Command sent successfully, Request ID: " + requestId);
        } catch (IOException e) {
            pendingResults.remove(requestId);
            future.completeExceptionally(e);
            System.err.println("[Client] ❌ Command send failed: " + e.getMessage());
            e.printStackTrace();
        }

        return future;
    }

    public void addResultListener(Consumer<ServerResult> listener) {
        resultListeners.add(Objects.requireNonNull(listener, "listener"));
    }

    public void removeResultListener(Consumer<ServerResult> listener) {
        resultListeners.remove(listener);
    }

    public void addStateUpdateListener(Consumer<StateUpdate> listener) {
        stateUpdateListeners.add(Objects.requireNonNull(listener, "listener"));
    }

    public void removeStateUpdateListener(Consumer<StateUpdate> listener) {
        stateUpdateListeners.remove(listener);
    }

    public void addNoticeListener(Consumer<ServerNotice> listener) {
        noticeListeners.add(Objects.requireNonNull(listener, "listener"));
    }

    public void removeNoticeListener(Consumer<ServerNotice> listener) {
        noticeListeners.remove(listener);
    }

    void handleMessage(Object message) {
        if (message instanceof ServerResult result) {
            CompletableFuture<ServerResult> pending = pendingResults.remove(result.requestId());
            if (pending != null) {
                pending.complete(result);
            }
            resultListeners.forEach(listener -> listener.accept(result));
            return;
        }
        if (message instanceof StateUpdate update) {
            stateUpdateListeners.forEach(listener -> listener.accept(update));
            return;
        }
        if (message instanceof ServerNotice notice) {
            noticeListeners.forEach(listener -> listener.accept(notice));
            return;
        }
        ServerNotice notice = new ServerNotice("UNKNOWN_MESSAGE",
                "Client received an unknown server message", Map.of(
                "messageType", message == null ? "null" : message.getClass().getName()));
        noticeListeners.forEach(listener -> listener.accept(notice));
    }

    void handleDisconnect(Exception cause) {
        if (!connected.getAndSet(false)) {
            return;
        }

        System.out.println("\n[Client] ⚠️  Disconnected from server");
        if (cause != null) {
            System.err.println("[Client] ❌ Disconnect reason: " + cause.getClass().getSimpleName() + " - " + cause.getMessage());
            cause.printStackTrace();

            if (cause instanceof ClassNotFoundException) {
                System.err.println("   🔍 Troubleshoot: Version mismatch between client and server, incompatible serialized class");
            } else if (cause instanceof java.io.InvalidClassException) {
                System.err.println("   🔍 Troubleshoot: SerialVersionUID mismatch or modified class structure");
            }
        } else {
            System.out.println("[Client] ℹ️  Normal connection closure");
        }

        CancellationException cancellation = new CancellationException("Client disconnected");
        if (cause != null) {
            cancellation.initCause(cause);
        }
        pendingResults.forEach((_, future) -> future.completeExceptionally(cancellation));
        pendingResults.clear();

        if (cause != null) {
            ServerNotice notice = new ServerNotice("CONNECTION_CLOSED", cause.getMessage(), Map.of());
            noticeListeners.forEach(listener -> listener.accept(notice));
        }
        closeSocket();
    }

    @Override
    public void close() {
        if (!connected.getAndSet(false)) {
            closeSocket();
            return;
        }
        pendingResults.forEach((_, future) ->
                future.completeExceptionally(new CancellationException("Client closed")));
        pendingResults.clear();
        closeSocket();
    }

    private synchronized void send(Object message) throws IOException {
        output.writeObject(message);
        output.flush();
        output.reset();
    }

    private void closeSocket() {
        if (socket == null) {
            return;
        }
        try {
            socket.close();
        } catch (IOException ignored) {
            // Best-effort shutdown.
        }
    }
}