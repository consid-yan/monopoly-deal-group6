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
        if (connected.get()) {
            return;
        }
        socket = new Socket(Objects.requireNonNull(host, "host"), port);
        output = new ObjectOutputStream(socket.getOutputStream());
        output.flush();
        var input = new ObjectInputStream(socket.getInputStream());
        connected.set(true);
        var gameThread = new GameThread(input, this);
        gameThread.start();
    }

    public boolean isConnected() {
        return connected.get();
    }

    public CompletableFuture<ServerResult> sendCommand(CommandType commandType, Map<String, Object> payload) {
        Objects.requireNonNull(commandType, "commandType");
        if (!connected.get()) {
            CompletableFuture<ServerResult> failed = new CompletableFuture<>();
            failed.completeExceptionally(new IllegalStateException("Client is not connected"));
            return failed;
        }

        String requestId = UUID.randomUUID().toString();
        CompletableFuture<ServerResult> future = new CompletableFuture<>();
        pendingResults.put(requestId, future);
        try {
            send(new ClientCommand(requestId, commandType, payload));
        } catch (IOException e) {
            pendingResults.remove(requestId);
            future.completeExceptionally(e);
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
