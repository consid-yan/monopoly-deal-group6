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
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.Consumer;
import javafx.application.Platform;

public final class UiSessionContext implements AutoCloseable {
    private static final int HOSTED_CLIENT_CONNECT_ATTEMPTS = 20;
    private static final long HOSTED_CLIENT_CONNECT_DELAY_MILLIS = 50L;
    private static final String JOIN_CODE_PREFIX = "MDJ1-";
    private static final String REJOIN_CODE_PREFIX = "MDR1-";
    private static final String CODE_SEPARATOR = "\n";

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

    public String joinCode() {
        if (host.isBlank() || port <= 0 || sessionCode.isBlank()) {
            return "";
        }
        return encodeConnectionCode(JOIN_CODE_PREFIX, host, Integer.toString(port), sessionCode);
    }

    public String rejoinCode() {
        String sessionIdentity = sessionCode.isBlank() ? sessionId : sessionCode;
        if (host.isBlank()
                || port <= 0
                || sessionIdentity.isBlank()
                || playerId.isBlank()
                || playerToken.isBlank()) {
            return "";
        }
        return encodeConnectionCode(
                REJOIN_CODE_PREFIX,
                host,
                Integer.toString(port),
                sessionIdentity,
                playerId,
                playerToken);
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
                GameClient newClient = openClientWithRetry("127.0.0.1", server.port());
                String shareableHost = resolveShareableHost();
                hostedServer = server;
                client = newClient;
                host = shareableHost;
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

    public CompletableFuture<ServerResult> joinSession(String requestedJoinCode, String requestedPlayerName) {
        try {
            JoinCode joinCode = parseJoinCode(requestedJoinCode);
            String newPlayerName = requireText(requestedPlayerName, "player name");
            String newPlayerId = generatePlayerId(newPlayerName);
            return connectAs(joinCode.host(), joinCode.port(), newPlayerId, newPlayerName, "")
                    .thenCompose(ignored -> sendCommand(CommandType.JOIN_SESSION, Map.of(
                            "sessionCode", joinCode.sessionCode(),
                            "playerId", newPlayerId,
                            "playerName", newPlayerName
                    )));
        } catch (RuntimeException e) {
            return failedFuture(e);
        }
    }

    public CompletableFuture<ServerResult> rejoinSession(String requestedRejoinCode) {
        try {
            RejoinCode rejoinCode = parseRejoinCode(requestedRejoinCode);
            return connectAs(
                    rejoinCode.host(),
                    rejoinCode.port(),
                    rejoinCode.playerId(),
                    rejoinCode.playerId(),
                    rejoinCode.playerToken())
                    .thenCompose(ignored -> sendCommand(CommandType.REJOIN_SESSION, Map.of(
                            "sessionIdentity", rejoinCode.sessionIdentity(),
                            "playerId", rejoinCode.playerId(),
                            "playerToken", rejoinCode.playerToken()
                    )));
        } catch (RuntimeException e) {
            return failedFuture(e);
        }
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
        String cleanHost = requireText(requestedHost, "host");
        if (requestedPort <= 0 || requestedPort > 65535) {
            throw new IllegalArgumentException("Port must be between 1 and 65535");
        }
        GameClient newClient = new GameClient();
        newClient.addStateUpdateListener(this::handleStateUpdate);
        newClient.addNoticeListener(this::handleNotice);
        try {
            newClient.connect(cleanHost, requestedPort);
            return newClient;
        } catch (IOException e) {
            newClient.close();
            throw new IOException(connectionFailureMessage(cleanHost, requestedPort), e);
        } catch (RuntimeException e) {
            newClient.close();
            throw e;
        }
    }

    private GameClient openClientWithRetry(String requestedHost, int requestedPort) throws IOException {
        IOException lastFailure = null;
        for (int attempt = 1; attempt <= HOSTED_CLIENT_CONNECT_ATTEMPTS; attempt++) {
            try {
                return openClient(requestedHost, requestedPort);
            } catch (IOException e) {
                lastFailure = e;
                if (attempt == HOSTED_CLIENT_CONNECT_ATTEMPTS) {
                    break;
                }
                waitBeforeRetry();
            }
        }
        throw lastFailure == null
                ? new IOException(connectionFailureMessage(requestedHost, requestedPort))
                : lastFailure;
    }

    private void waitBeforeRetry() throws IOException {
        try {
            Thread.sleep(HOSTED_CLIENT_CONNECT_DELAY_MILLIS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while waiting for the local game server to accept connections", e);
        }
    }

    private String connectionFailureMessage(String requestedHost, int requestedPort) {
        return "Could not connect to " + requestedHost + ":" + requestedPort
                + ". Make sure the game server is running and the host/port are correct. "
                + "For Join Game, use the Join Code shown in the host lobby.";
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

    private CompletableFuture<ServerResult> failedFuture(RuntimeException error) {
        CompletableFuture<ServerResult> failed = new CompletableFuture<>();
        failed.completeExceptionally(error);
        return failed;
    }

    private String encodeConnectionCode(String prefix, String... values) {
        String payload = String.join(CODE_SEPARATOR, values);
        return prefix + Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(payload.getBytes(StandardCharsets.UTF_8));
    }

    private JoinCode parseJoinCode(String rawCode) {
        String code = requireText(rawCode, "join code");
        if (code.startsWith(JOIN_CODE_PREFIX)) {
            List<String> parts = decodeConnectionCode(code, JOIN_CODE_PREFIX, "Join code");
            if (parts.size() == 3) {
                return new JoinCode(
                        requireText(parts.get(0), "join host"),
                        parseCodePort(parts.get(1)),
                        requireText(parts.get(2), "session code"));
            }
        }
        String[] legacyParts = code.split(":", 3);
        if (legacyParts.length == 3) {
            return new JoinCode(
                    requireText(legacyParts[0], "join host"),
                    parseCodePort(legacyParts[1]),
                    requireText(legacyParts[2], "session code"));
        }
        throw new IllegalArgumentException("Join code is invalid. Use the Join Code shown in the host lobby.");
    }

    private RejoinCode parseRejoinCode(String rawCode) {
        String code = requireText(rawCode, "rejoin code");
        if (code.startsWith(REJOIN_CODE_PREFIX)) {
            List<String> parts = decodeConnectionCode(code, REJOIN_CODE_PREFIX, "Rejoin code");
            if (parts.size() == 5) {
                return new RejoinCode(
                        requireText(parts.get(0), "rejoin host"),
                        parseCodePort(parts.get(1)),
                        requireText(parts.get(2), "session identity"),
                        requireText(parts.get(3), "player id"),
                        requireText(parts.get(4), "player token"));
            }
        }
        String[] legacyParts = code.split(":", 5);
        if (legacyParts.length == 5) {
            return new RejoinCode(
                    requireText(legacyParts[0], "rejoin host"),
                    parseCodePort(legacyParts[1]),
                    requireText(legacyParts[2], "session identity"),
                    requireText(legacyParts[3], "player id"),
                    requireText(legacyParts[4], "player token"));
        }
        throw new IllegalArgumentException("Rejoin code is invalid. Use the Rejoin Code shown in the lobby.");
    }

    private List<String> decodeConnectionCode(String code, String prefix, String label) {
        try {
            String encodedPayload = code.substring(prefix.length());
            String payload = new String(Base64.getUrlDecoder().decode(encodedPayload), StandardCharsets.UTF_8);
            return Arrays.asList(payload.split(CODE_SEPARATOR, -1));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(label + " is invalid.", e);
        }
    }

    private int parseCodePort(String rawPort) {
        try {
            int parsedPort = Integer.parseInt(requireText(rawPort, "port"));
            if (parsedPort <= 0 || parsedPort > 65535) {
                throw new IllegalArgumentException("Port must be between 1 and 65535");
            }
            return parsedPort;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Port must be a number", e);
        }
    }

    private String resolveShareableHost() {
        String fallback = null;
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces != null && interfaces.hasMoreElements()) {
                NetworkInterface networkInterface = interfaces.nextElement();
                if (!isUsableNetworkInterface(networkInterface)) {
                    continue;
                }
                Enumeration<InetAddress> addresses = networkInterface.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress address = addresses.nextElement();
                    if (isShareableIpv4(address)) {
                        if (address.isSiteLocalAddress()) {
                            return address.getHostAddress();
                        }
                        if (fallback == null) {
                            fallback = address.getHostAddress();
                        }
                    }
                }
            }

            InetAddress localHost = InetAddress.getLocalHost();
            if (isShareableIpv4(localHost)) {
                return localHost.getHostAddress();
            }
        } catch (IOException ignored) {
            // Fall back to loopback for single-machine play.
        }
        if (fallback != null) {
            return fallback;
        }
        return "127.0.0.1";
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

    private record JoinCode(String host, int port, String sessionCode) {}

    private record RejoinCode(String host, int port, String sessionIdentity, String playerId, String playerToken) {}

    @Override
    public void close() {
        resetNetwork();
    }
}
