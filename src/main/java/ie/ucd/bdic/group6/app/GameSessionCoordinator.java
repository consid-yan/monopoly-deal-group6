package ie.ucd.bdic.group6.app;

import ie.ucd.bdic.group6.command.CommandType;
import ie.ucd.bdic.group6.command.Command;
import ie.ucd.bdic.group6.core.snapshot.SessionSnapshot;
import ie.ucd.bdic.group6.network.client.GameClient;
import ie.ucd.bdic.group6.network.message.ServerNotice;
import ie.ucd.bdic.group6.network.message.ServerResponse;
import ie.ucd.bdic.group6.network.message.ServerUpdate;
import ie.ucd.bdic.group6.network.server.GameServer;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

import ie.ucd.bdic.group6.ui.UiSnapshot;
import javafx.application.Platform;

import static ie.ucd.bdic.group6.network.NetworkAddressResolver.resolveShareableHost;
import static ie.ucd.bdic.group6.network.NetworkConfig.DEFAULT_PORT;
import static ie.ucd.bdic.group6.network.NetworkConfig.requireValidPort;

public final class GameSessionCoordinator implements AutoCloseable {
    private static final Logger LOGGER = Logger.getLogger(GameSessionCoordinator.class.getName());
    private final AppConfig appConfig;

    private volatile GameClient client;
    private volatile GameServer hostedServer;
    private volatile UiSnapshot latestSnapshot = UiSnapshot.empty();
    private volatile Consumer<UiSnapshot> snapshotListener = _ -> {};
    private volatile Consumer<String> messageListener = _ -> {};
    private volatile Consumer<String> disconnectListener = _ -> {};

    private volatile String host = "";
    private volatile String playerId = "";
    private volatile String playerName = "";
    private volatile String playerToken = "";
    private volatile String sessionId = "";
    private volatile String roomCode = "";
    private final Map<String, RejoinCredentials> rejoinCredentialsByRoomCode = new ConcurrentHashMap<>();
    private volatile boolean connected;

    public GameSessionCoordinator(AppConfig config) {
        appConfig = Objects.requireNonNull(config, "appConfig");
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

    public String roomCode() {
        return roomCode;
    }

    public boolean isConnected() {
        GameClient currentClient = client;
        return connected && currentClient != null && currentClient.isConnected();
    }

    public String joinHost() {
        return host;
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

    public void setDisconnectListener(Consumer<String> listener) {
        disconnectListener = listener == null ? _ -> {} : listener;
    }

    public CompletableFuture<ServerResponse> createHostedSession(String requestedPlayerName, int maxPlayers) {
        String newPlayerName = requireText(requestedPlayerName, "player name");
        String newPlayerId = generatePlayerId(newPlayerName);

        LOGGER.info("\n[1/5] Starting hosted game session for player "
                + newPlayerName + " (" + newPlayerId + ")");

        return CompletableFuture.supplyAsync(() -> {
            GameServer server = null;
            try {
                LOGGER.info("[2/5] Resetting network state...");
                resetNetwork();
                LOGGER.info("Network state reset completed.");

                LOGGER.info(() -> "[3/5] Starting game server, attempting to bind port " + DEFAULT_PORT);
                server = appConfig.gameServer(DEFAULT_PORT);
                server.startAsync();

                int actualPort = server.port();
                String serverBindAddress = "0.0.0.0";

                LOGGER.info("Server started successfully.");
                LOGGER.info(() -> "Bind address: " + serverBindAddress);
                LOGGER.info(() -> "Actual listening port: " + actualPort);

                String shareableHost = resolveShareableHost();

                LOGGER.info("[4/5] Creating local client and connecting to server...");
                GameClient newClient = openClient("127.0.0.1", actualPort);
                LOGGER.info("Local client connected to server successfully.");

                hostedServer = server;
                client = newClient;
                host = shareableHost;
                playerId = newPlayerId;
                playerName = newPlayerName;
                connected = true;

                LOGGER.info("Local session status initialized.");

                return null;
            } catch (IOException e) {
                LOGGER.log(
                        Level.SEVERE,
                        "Failed to start server: I/O exception. Possible reasons: port "
                                + DEFAULT_PORT
                                + " occupied, insufficient permissions, or firewall block.",
                        e);

                try {
                    server.stop();
                    LOGGER.info("Attempted to shut down leftover server process.");
                } catch (Exception ex) {
                    LOGGER.log(Level.WARNING, "Failed to close leftover server.", ex);
                }

                throw new CompletionException("Failed to start server: " + e.getMessage(), e);
            } catch (RuntimeException runtimeException) {
                LOGGER.log(Level.SEVERE, "Failed to start server: runtime exception.", runtimeException);

                if (server != null) {
                    try {
                        server.stop();
                    } catch (Exception exception) {
                        LOGGER.log(Level.WARNING, "Failed to close leftover server after runtime exception.", exception);
                    }
                }

                throw new CompletionException("Failed to start server: " + runtimeException.getMessage(), runtimeException);
            }
        }).thenCompose(ignored -> {
            LOGGER.info("[5/5] Sending create session command to server...");

            return sendCommand(CommandType.CREATE_SESSION, Map.of(
                    "hostPlayerId", newPlayerId,
                    "playerName", newPlayerName,
                    "maxPlayers", maxPlayers
            )).whenComplete((result, throwable) -> {
                if (throwable != null) {
                    LOGGER.log(Level.SEVERE,
                            "Failed to create game session. Possible reasons: no server response, "
                                    + "serialization failure, or invalid command format.",
                            throwable);
                } else if (result.accepted()) {
                    LOGGER.info("Game session created successfully.");
                    LOGGER.info(() -> "Session ID: " + result.payload().get("sessionId"));
                    LOGGER.info(() -> "Room Code: " + result.payload().get("roomCode"));
                } else {
                    LOGGER.warning(() -> "Server rejected session creation. Error Code: "
                            + result.code()
                            + ", Error Message: "
                            + result.message());
                }
            });
        });
    }

    public CompletableFuture<ServerResponse> joinSession(
            String requestedHost,
            String requestedRoomCode,
            String requestedPlayerName) {
        String newHost = requireText(requestedHost, "host");
        String newRoomCode = requireText(requestedRoomCode, "room code");
        String newPlayerName = requireText(requestedPlayerName, "player name");
        String newPlayerId = generatePlayerId(newPlayerName);
        return connectAs(newHost, newPlayerId, newPlayerName, "")
                .thenCompose(ignored -> sendCommand(CommandType.JOIN_SESSION, Map.of(
                        "roomCode", newRoomCode,
                        "playerId", newPlayerId,
                        "playerName", newPlayerName)));
    }

    public CompletableFuture<ServerResponse> rejoinSession(String requestedRoomCode) {
        RejoinCredentials credentials;
        try {
            credentials = requireSavedRejoinCredentials(requestedRoomCode);
        } catch (RuntimeException exception) {
            return CompletableFuture.failedFuture(exception);
        }
        return connectAs(
                credentials.host(),
                credentials.playerId(),
                credentials.playerId(),
                credentials.playerToken())
                .thenCompose(ignored -> sendCommand(CommandType.REJOIN_SESSION, Map.of(
                        "sessionIdentity", credentials.roomCode(),
                        "playerId", credentials.playerId(),
                        "playerToken", credentials.playerToken()
                )));
    }

    public CompletableFuture<ServerResponse> sendCommand(CommandType commandType, Map<String, Object> payload) {
        GameClient currentClient = client;
        if (currentClient == null || !currentClient.isConnected()) {
            CompletableFuture<ServerResponse> failed = new CompletableFuture<>();
            failed.completeExceptionally(new IllegalStateException("Not connected to a game server"));
            failed.whenComplete((_, error) -> notifyError(error));
            return failed;
        }
        CompletableFuture<ServerResponse> future = currentClient.sendCommand(commandType, withPlayerToken(payload));
        future.whenComplete((result, error) -> {
            if (error != null) {
                if (connected) {
                    notifyError(error);
                }
                return;
            }
            handleResult(result);
        });
        return future;
    }

    public CompletableFuture<ServerResponse> sendCommand(Command command) {
        Objects.requireNonNull(command, "command");
        return sendCommand(command.getCommandType(), command.payload());
    }

    public void leaveSession() {
        resetNetwork();
        latestSnapshot = UiSnapshot.empty();
        sessionId = "";
        roomCode = "";
        playerId = "";
        playerName = "";
        playerToken = "";
        notifySnapshot(latestSnapshot);
    }

    private CompletableFuture<Void> connectAs(
            String requestedHost,
            String newPlayerId,
            String newPlayerName,
            String newPlayerToken) {
        return CompletableFuture.runAsync(() -> {
            try {
                resetNetwork();
                client = openClient(requestedHost, DEFAULT_PORT);
                hostedServer = null;
                host = requestedHost;
                playerId = newPlayerId;
                playerName = newPlayerName;
                playerToken = newPlayerToken == null ? "" : newPlayerToken;
                connected = true;
            } catch (IOException | RuntimeException e) {
                throw new CompletionException(e);
            }
        });
    }

    private GameClient openClient(String requestedHost, int requestedPort) throws IOException {
        requireValidPort(requestedPort);
        GameClient newClient = new GameClient();
        newClient.addUpdateListener(this::handleStateUpdate);
        newClient.addNoticeListener(this::handleNotice);
        newClient.addDisconnectListener(this::handleClientDisconnect);
        newClient.connect(requestedHost, requestedPort);
        return newClient;
    }

    private synchronized void resetNetwork() {
        connected = false;
        playerToken = "";
        closeAndClearNetworkResources();
    }

    private void handleStateUpdate(ServerUpdate update) {
        if (update == null) {
            return;
        }
        updateSnapshot(update.snapshot());
    }

    private void handleResult(ServerResponse response) {
        if (response == null) {
            return;
        }
        if (response.accepted()) {
            rememberPlayerToken(response.payload());
            if (response.snapshot() != null) {
                updateSnapshot(response.snapshot());
            }
            return;
        }
        notifyMessage(response.message());
    }

    private void handleNotice(ServerNotice notice) {
        if (notice == null) {
            return;
        }
        String message = text(notice.payload().get("message"));
        notifyMessage(message == null ? notice.type().name() : notice.type().name() + ": " + message);
    }

    private void handleClientDisconnect(Throwable cause) {
        if (!resetAfterUnexpectedDisconnect()) {
            return;
        }
        if (cause == null) {
            LOGGER.warning("Lost connection to the game server.");
        } else {
            LOGGER.log(Level.WARNING, "Lost connection to the game server.", cause);
        }
        runOnFx(() -> disconnectListener.accept(
                "Connection to the game server was lost. Returning to the main menu."));
    }

    private synchronized boolean resetAfterUnexpectedDisconnect() {
        if (!connected && client == null) {
            return false;
        }

        connected = false;
        latestSnapshot = UiSnapshot.empty();
        host = "";
        playerId = "";
        playerName = "";
        playerToken = "";
        sessionId = "";
        roomCode = "";
        closeAndClearNetworkResources();

        notifySnapshot(latestSnapshot);
        return true;
    }

    private void closeAndClearNetworkResources() {
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

    private void updateSnapshot(SessionSnapshot sessionSnapshot) {
        if (sessionSnapshot == null) {
            return;
        }
        UiSnapshot uiSnapshot = UiSnapshot.from(sessionSnapshot);
        latestSnapshot = uiSnapshot;
        if (!uiSnapshot.sessionId().isBlank()) {
            sessionId = uiSnapshot.sessionId();
        }
        if (!uiSnapshot.roomCode().isBlank()) {
            roomCode = uiSnapshot.roomCode();
        }
        notifySnapshot(uiSnapshot);
    }

    private Map<String, Object> withPlayerToken(Map<String, Object> payload) {
        Map<String, Object> copy = new LinkedHashMap<>();
        if (payload != null) {
            copy.putAll(payload);
        }
        if (!playerToken.isBlank()) {
            copy.putIfAbsent("playerToken", playerToken);
        }
        return copy;
    }

    private void rememberPlayerToken(Map<String, Object> data) {
        if (data == null) {
            return;
        }
        String token = text(data.get("playerToken"));
        if (token != null) {
            playerToken = token;
            rememberRejoinCredentials(data, token);
        }
    }

    private void rememberRejoinCredentials(Map<String, Object> data, String token) {
        String rememberedSessionId = firstNonBlank(text(data.get("sessionId")), sessionId);
        String rememberedRoomCode = firstNonBlank(text(data.get("roomCode")), roomCode);
        String rememberedPlayerId = firstNonBlank(playerId, text(data.get("playerId")));

        if (host.isBlank() || rememberedRoomCode == null || rememberedPlayerId == null || token.isBlank()) {
            return;
        }

        RejoinCredentials credentials = new RejoinCredentials(
                host,
                blankToEmpty(rememberedSessionId),
                rememberedRoomCode,
                rememberedPlayerId,
                token);
        rejoinCredentialsByRoomCode.put(normalizeRoomCode(rememberedRoomCode), credentials);
    }

    private RejoinCredentials requireSavedRejoinCredentials(String requestedRoomCode) {
        String normalizedRoomCode = normalizeRoomCode(requireText(requestedRoomCode, "room code"));
        RejoinCredentials credentials = rejoinCredentialsByRoomCode.get(normalizedRoomCode);
        if (credentials != null) {
            return credentials;
        }

        throw new IllegalArgumentException(
                "No saved player identity is available for room code " + normalizedRoomCode + ". "
                        + "Create or join the session from this app before rejoining.");
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

    private String text(Object value) {
        if (value == null) {
            return null;
        }
        String text = value.toString().trim();
        return text.isEmpty() ? null : text;
    }

    private String firstNonBlank(String first, String second) {
        if (first != null && !first.isBlank()) {
            return first;
        }
        if (second != null && !second.isBlank()) {
            return second;
        }
        return null;
    }

    private String blankToEmpty(String value) {
        return value == null ? "" : value;
    }

    private String normalizeRoomCode(String value) {
        return value.trim().toUpperCase(Locale.ROOT);
    }

    private String requireText(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(label + " is required");
        }
        return value.trim();
    }

    private record RejoinCredentials(
            String host,
            String sessionId,
            String roomCode,
            String playerId,
            String playerToken) {}

    @Override
    public void close() {
        resetNetwork();
    }
}
