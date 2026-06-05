package ie.ucd.bdic.group6.ui;

import ie.ucd.bdic.group6.app.AppConfig;
import ie.ucd.bdic.group6.command.CommandType;
import ie.ucd.bdic.group6.network.client.GameClient;
import ie.ucd.bdic.group6.network.protocol.ServerNotice;
import ie.ucd.bdic.group6.network.protocol.ServerResult;
import ie.ucd.bdic.group6.network.protocol.StateUpdate;
import ie.ucd.bdic.group6.network.server.GameServer;

import java.io.IOException;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Enumeration;
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
    private volatile String playerToken = "";
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

    public String playerToken() {
        return playerToken;
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

    public String joinHost() {
        return host;
    }

    public String joinPort() {
        if (port <= 0) {
            return "";
        }
        return Integer.toString(port);
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

        System.out.println("\n[1/5] Starting creation of hosted game session, player name: " + newPlayerName + ", player ID: " + newPlayerId);

        return CompletableFuture.supplyAsync(() -> {
            GameServer server = null;
            try {
                System.out.println("[2/5] Resetting network state...");
                resetNetwork();
                System.out.println("✅ Network state reset completed");

                System.out.println("[3/5] Starting game server, attempting to bind port 8888...");
                server = appConfig.gameServer(8888);
                server.startAsync();

                int actualPort = server.port();
                String serverBindAddress = "0.0.0.0";
                System.out.println("✅ Server started successfully!");
                System.out.println("   Bind address: " + serverBindAddress + " (0.0.0.0 listens on all network adapters)");
                System.out.println("   Actual listening port: " + actualPort);

                String shareableHost = resolveShareableHost();
                System.out.println("✅ Resolved local LAN IP: " + shareableHost);
                System.out.println("\n📢 Connection address for other players: " + shareableHost + ":" + actualPort);
                System.out.println("📢 Local connection address: 127.0.0.1:" + actualPort + "\n");

                System.out.println("[4/5] Creating local client and connecting to server...");
                GameClient newClient = openClient("127.0.0.1", actualPort);
                System.out.println("✅ Local client connected to server successfully");

                // Assign shared variables
                hostedServer = server;
                client = newClient;
                host = shareableHost;
                port = actualPort;
                playerId = newPlayerId;
                playerName = newPlayerName;
                connected = true;
                System.out.println("✅ Local session status initialized");

                return null;
            } catch (IOException e) {
                System.err.println("\n❌ Failed to start server: IO Exception");
                System.err.println("   Error message: " + e.getMessage());
                System.err.println("   Possible reasons: port 8888 occupied, insufficient permissions, firewall block");
                e.printStackTrace();

                if (server != null) {
                    try {
                        server.stop();
                        System.out.println("✅ Attempted to shut down leftover server process");
                    } catch (Exception ex) {
                        System.err.println("⚠️  Failed to close leftover server: " + ex.getMessage());
                    }
                }
                throw new CompletionException("Failed to start server: " + e.getMessage(), e);
            } catch (RuntimeException e) {
                System.err.println("\n❌ Failed to start server: Runtime Exception");
                System.err.println("   Error message: " + e.getMessage());
                e.printStackTrace();

                if (server != null) {
                    try {
                        server.stop();
                    } catch (Exception ex) {
                        // ignore
                    }
                }
                throw new CompletionException("Failed to start server: " + e.getMessage(), e);
            }
        }).thenCompose(ignored -> {
            System.out.println("[5/5] Sending create session command to server...");
            return sendCommand(CommandType.CREATE_SESSION, Map.of(
                    "hostPlayerId", newPlayerId,
                    "playerName", newPlayerName,
                    "maxPlayers", maxPlayers
            )).whenComplete((result, throwable) -> {
                if (throwable != null) {
                    System.err.println("\n❌ Failed to create game session");
                    System.err.println("   Error message: " + throwable.getMessage());
                    System.err.println("   Possible reasons: no server response, serialization failure, invalid command format");
                    throwable.printStackTrace();
                } else {
                    if (result.success()) {
                        System.out.println("\n✅ Game session created successfully!");
                        System.out.println("   Session ID: " + result.data().get("sessionId"));
                        System.out.println("   Session Code: " + result.data().get("sessionCode"));
                        System.out.println("🎉 Ready to invite other players to join!\n");
                    } else {
                        System.err.println("\n❌ Server rejected session creation");
                        System.err.println("   Error Code: " + result.code());
                        System.err.println("   Error Message: " + result.message());
                    }
                }
            });
        });
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
        return connectAs(newHost, requestedPort, newPlayerId, newPlayerName, "")
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
            String requestedPlayerId,
            String requestedPlayerToken) {
        String rejoinPlayerId = requireText(requestedPlayerId, "player id");
        String rejoinPlayerToken = requireText(requestedPlayerToken, "player token");
        return connectAs(
                requireText(requestedHost, "host"),
                requestedPort,
                rejoinPlayerId,
                rejoinPlayerId,
                rejoinPlayerToken)
                .thenCompose(ignored -> sendCommand(CommandType.REJOIN_SESSION, Map.of(
                        "sessionIdentity", requireText(sessionIdentity, "session identity"),
                        "playerId", rejoinPlayerId,
                        "playerToken", rejoinPlayerToken
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
        CompletableFuture<ServerResult> future = currentClient.sendCommand(commandType, withPlayerToken(payload));
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
        if (!playerToken.isBlank()) {
            payload.put("playerToken", playerToken);
        }
        return payload;
    }

    public void leaveSession() {
        resetNetwork();
        latestSnapshot = UiSnapshot.empty();
        sessionId = "";
        sessionCode = "";
        playerId = "";
        playerName = "";
        playerToken = "";
        notifySnapshot(latestSnapshot);
    }

    private CompletableFuture<Void> connectAs(
            String requestedHost,
            int requestedPort,
            String newPlayerId,
            String newPlayerName,
            String newPlayerToken) {
        return CompletableFuture.runAsync(() -> {
            try {
                resetNetwork();
                client = openClient(requestedHost, requestedPort);
                hostedServer = null;
                host = requestedHost;
                port = requestedPort;
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
        playerToken = "";
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
            rememberPlayerToken(result.data());
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
        }
    }

    /**
     * Correctly get local LAN IPv4 address (exclude loopback, virtual adapters, VPN adapters)
     * Fix issue returning 127.0.0.1 which blocks cross-device connection
     */
    private String resolveShareableHost() {
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface ni = interfaces.nextElement();

                // Skip loopback, virtual and disabled network interfaces
                if (ni.isLoopback() || ni.isVirtual() || !ni.isUp()) {
                    continue;
                }

                // Skip common virtual adapters: Docker, VMware, VirtualBox, WSL
                String interfaceName = ni.getName().toLowerCase();
                if (interfaceName.contains("docker")
                        || interfaceName.contains("vmnet")
                        || interfaceName.contains("vbox")
                        || interfaceName.contains("wsl")) {
                    continue;
                }

                Enumeration<InetAddress> addresses = ni.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress addr = addresses.nextElement();

                    // Only pick valid IPv4, skip IPv6 and loopback
                    if (addr instanceof Inet4Address && !addr.isLoopbackAddress()) {
                        return addr.getHostAddress();
                    }
                }
            }

            // Fallback to loopback if no valid LAN IP found (local-only access)
            System.err.println("⚠️  No valid LAN IP found, local connection only");
            return "127.0.0.1";
        } catch (SocketException e) {
            throw new RuntimeException("Failed to resolve local host IP", e);
        }
    }

    private boolean isUsableNetworkInterface(NetworkInterface networkInterface) {
        try {
            return networkInterface.isUp()
                    && !networkInterface.isLoopback()
                    && !networkInterface.isVirtual();
        } catch (SocketException e) {
            return false;
        }
    }

    private boolean isShareableIpv4(InetAddress address) {
        return address instanceof Inet4Address
                && !address.isLoopbackAddress()
                && !address.isLinkLocalAddress()
                && !address.isAnyLocalAddress();
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