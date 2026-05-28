package ie.ucd.bdic.group6.ui;

import ie.ucd.bdic.group6.app.AppConfig;
import ie.ucd.bdic.group6.command.CommandType;
import ie.ucd.bdic.group6.network.client.GameClient;
import ie.ucd.bdic.group6.network.protocol.ServerNotice;
import ie.ucd.bdic.group6.network.protocol.ServerResult;
import ie.ucd.bdic.group6.network.protocol.StateUpdate;
import ie.ucd.bdic.group6.network.server.GameServer;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.Consumer;
import javafx.application.Platform;

public final class UiSessionContext implements AutoCloseable {
    private final AppConfig appConfig;

    private volatile GameClient client;
    private volatile GameServer hostedServer;
    private volatile UiSnapshot latestSnapshot = UiSnapshot.empty();
    private volatile Consumer<UiSnapshot> snapshotListener = _ -> {};
    private volatile Consumer<String> messageListener = _ -> {};

    private volatile String host = "";
    private volatile int port;
    private volatile String playerId = "";
    private volatile String playerName = "";
    private volatile String sessionId = "";
    private volatile String sessionCode = "";
    private volatile boolean connected;

    public UiSessionContext(AppConfig appConfig) {
        this.appConfig = Objects.requireNonNull(appConfig, "appConfig");
    }

    public UiSnapshot latestSnapshot() {
        return latestSnapshot;
    }

    public String playerId() {
        return playerId;
    }

    public String playerName() {
        return playerName;
    }

    public String sessionId() {
        return sessionId;
    }

    public String sessionCode() {
        return sessionCode;
    }

    public boolean isConnected() {
        GameClient currentClient = client;
        return connected && currentClient != null && currentClient.isConnected();
    }

    public String connectionLabel() {
        if (isConnected()) {
            return host + ":" + port;
        }
        return "Disconnected";
    }

    public void setSnapshotListener(Consumer<UiSnapshot> listener) {
        snapshotListener = listener == null ? _ -> {
        } : listener;
        UiSnapshot snapshot = latestSnapshot;
        if (!snapshot.isEmpty()) {
            runOnFx(() -> snapshotListener.accept(snapshot));
        }
    }

    public void setMessageListener(Consumer<String> listener) {
        messageListener = listener == null ? _ -> {
        } : listener;
    }

    public CompletableFuture<ServerResult> createHostedSession(String requestedPlayerName, int maxPlayers) {
        String newPlayerName = requireText(requestedPlayerName, "player name");
        String newPlayerId = generatePlayerId(newPlayerName);
        return CompletableFuture.supplyAsync(() -> {
            GameServer server = null;
            try {
                resetNetwork();
                server = appConfig.gameServer(0);
                server.startAsync();
                GameClient newClient = openClient("127.0.0.1", server.port());
                hostedServer = server;
                client = newClient;
                host = "127.0.0.1";
                port = server.port();
                playerId = newPlayerId;
                playerName = newPlayerName;
                connected = true;
                return null;
            } catch (IOException | RuntimeException e) {
                if (server != null) {
                    server.stop();
                }
                throw new CompletionException(e);
            }
        }).thenCompose(ignored -> sendCommand(CommandType.CREATE_SESSION, Map.of(
                "hostPlayerId", newPlayerId,
                "playerName", newPlayerName,
                "maxPlayers", maxPlayers
        )));
    }

    public CompletableFuture<ServerResult> joinSession(
            String requestedHost,
            int requestedPort,
            String requestedSessionCode,
            String requestedPlayerName) {
        String newHost = requireText(requestedHost, "host");
        String newSessionCode = requireText(requestedSessionCode, "session code");
        String newPlayerName = requireText(requestedPlayerName, "player name");
        String newPlayerId = generatePlayerId(newPlayerName);
        return connectAs(newHost, requestedPort, newPlayerId, newPlayerName)
                .thenCompose(ignored -> sendCommand(CommandType.JOIN_SESSION, Map.of(
                        "sessionCode", newSessionCode,
                        "playerId", newPlayerId,
                        "playerName", newPlayerName
                )));
    }

    public CompletableFuture<ServerResult> rejoinSession(
            String requestedHost,
            int requestedPort,
            String sessionIdentity,
            String requestedPlayerId) {
        String rejoinPlayerId = requireText(requestedPlayerId, "player id");
        return connectAs(
                requireText(requestedHost, "host"),
                requestedPort,
                rejoinPlayerId,
                rejoinPlayerId)
                .thenCompose(ignored -> sendCommand(CommandType.REJOIN_SESSION, Map.of(
                        "sessionIdentity", requireText(sessionIdentity, "session identity"),
                        "playerId", rejoinPlayerId
                )));
    }

    public CompletableFuture<ServerResult> sendCommand(CommandType commandType, Map<String, Object> payload) {
        GameClient currentClient = client;
        if (currentClient == null || !currentClient.isConnected()) {
            CompletableFuture<ServerResult> failed = new CompletableFuture<>();
            failed.completeExceptionally(new IllegalStateException("Not connected to a game server"));
            failed.whenComplete((_, error) -> notifyError(error));
            return failed;
        }
        CompletableFuture<ServerResult> future = currentClient.sendCommand(commandType, payload);
        future.whenComplete((result, error) -> {
            if (error != null) {
                notifyError(error);
                return;
            }
            handleResult(result);
        });
        return future;
    }

    public Map<String, Object> sessionCommandPayload() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("sessionId", sessionId);
        payload.put("playerId", playerId);
        return payload;
    }

    public void leaveSession() {
        resetNetwork();
        latestSnapshot = UiSnapshot.empty();
        sessionId = "";
        sessionCode = "";
        playerId = "";
        playerName = "";
        notifySnapshot(latestSnapshot);
    }

    private CompletableFuture<Void> connectAs(String requestedHost, int requestedPort, String newPlayerId, String newPlayerName) {
        return CompletableFuture.runAsync(() -> {
            try {
                resetNetwork();
                client = openClient(requestedHost, requestedPort);
                hostedServer = null;
                host = requestedHost;
                port = requestedPort;
                playerId = newPlayerId;
                playerName = newPlayerName;
                connected = true;
            } catch (IOException | RuntimeException e) {
                throw new CompletionException(e);
            }
        });
    }

    private GameClient openClient(String requestedHost, int requestedPort) throws IOException {
        if (requestedPort < 0 || requestedPort > 65535) {
            throw new IllegalArgumentException("Port must be between 0 and 65535");
        }
        GameClient newClient = new GameClient();
        newClient.addStateUpdateListener(this::handleStateUpdate);
        newClient.addNoticeListener(this::handleNotice);
        newClient.connect(requestedHost, requestedPort);
        return newClient;
    }

    private synchronized void resetNetwork() {
        connected = false;
        GameClient currentClient = client;
        client = null;
        if (currentClient != null) {
            currentClient.close();
        }
        GameServer currentServer = hostedServer;
        hostedServer = null;
        if (currentServer != null) {
            currentServer.stop();
        }
    }

    private void handleStateUpdate(StateUpdate update) {
        if (update == null) {
            return;
        }
        sessionId = update.sessionId();
        updateSnapshot(update.data());
    }

    private void handleResult(ServerResult result) {
        if (result == null) {
            return;
        }
        if (result.success()) {
            if (result.data() != null && !result.data().isEmpty()) {
                updateSnapshot(result.data());
            }
            return;
        }
        notifyMessage(result.code() + ": " + result.message());
    }

    private void handleNotice(ServerNotice notice) {
        if (notice == null) {
            return;
        }
        if ("CONNECTION_CLOSED".equals(notice.type()) || "SESSION_REPLACED".equals(notice.type())) {
            connected = false;
        }
        notifyMessage(notice.type() + ": " + notice.message());
    }

    private void updateSnapshot(Map<String, Object> data) {
        UiSnapshot snapshot = UiSnapshot.from(data);
        latestSnapshot = snapshot;
        if (!snapshot.sessionId().isBlank()) {
            sessionId = snapshot.sessionId();
        }
        if (!snapshot.sessionCode().isBlank()) {
            sessionCode = snapshot.sessionCode();
        }
        notifySnapshot(snapshot);
    }

    private void notifySnapshot(UiSnapshot snapshot) {
        runOnFx(() -> snapshotListener.accept(snapshot));
    }

    private void notifyMessage(String message) {
        if (message == null || message.isBlank()) {
            return;
        }
        runOnFx(() -> messageListener.accept(message));
    }

    private void notifyError(Throwable error) {
        Throwable cause = error instanceof CompletionException && error.getCause() != null
                ? error.getCause()
                : error;
        notifyMessage(cause == null ? "Unknown UI/network error" : cause.getMessage());
    }

    private void runOnFx(Runnable runnable) {
        if (Platform.isFxApplicationThread()) {
            runnable.run();
        } else {
            Platform.runLater(runnable);
        }
    }

    private String generatePlayerId(String sourceName) {
        String normalized = sourceName.toLowerCase(java.util.Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-|-$)", "");
        if (normalized.isBlank()) {
            normalized = "player";
        }
        return normalized + "-" + UUID.randomUUID().toString().substring(0, 8);
    }

    private String requireText(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(label + " is required");
        }
        return value.trim();
    }

    @Override
    public void close() {
        resetNetwork();
    }
}
