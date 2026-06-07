package ie.ucd.bdic.group6.integration;

import ie.ucd.bdic.group6.app.AppConfig;
import ie.ucd.bdic.group6.command.CommandType;
import ie.ucd.bdic.group6.controller.GameController;
import ie.ucd.bdic.group6.controller.mapper.RequestMapper;
import ie.ucd.bdic.group6.core.engine.GameEngine;
import ie.ucd.bdic.group6.facade.GameFacade;
import ie.ucd.bdic.group6.network.client.GameClient;
import ie.ucd.bdic.group6.network.message.NoticeType;
import ie.ucd.bdic.group6.network.message.ServerNotice;
import ie.ucd.bdic.group6.network.message.ServerResponse;
import ie.ucd.bdic.group6.network.message.ServerUpdate;
import ie.ucd.bdic.group6.network.server.GameServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

import static ie.ucd.bdic.group6.network.NetworkConfig.DEFAULT_PORT;
import static org.junit.jupiter.api.Assertions.*;

class NetworkIntegrationTest {
    private final List<GameClient> clients = new ArrayList<>();
    private GameServer server;

    @AfterEach
    void tearDown() {
        clients.forEach(GameClient::close);
        if (server != null) {
            server.stop();
            assertFalse(server.isRunning());
        }
    }

    @Test
    void loopbackClientsExecuteCommandsReceivePrivateStateAndRejoin() throws Exception {
        server = new AppConfig().gameServer(DEFAULT_PORT);
        server.startAsync();
        assertTrue(server.isRunning());
        assertTrue(server.port() > 0);

        GameClient host = connectedClient();
        GameClient player2 = connectedClient();
        List<ServerNotice> hostNotices = notices(host);
        List<ServerNotice> player2Notices = notices(player2);
        List<ServerUpdate> hostUpdates = updates(host);
        List<ServerUpdate> player2Updates = updates(player2);

        await(hostNotices, notice -> notice.type() == NoticeType.CONNECTED);
        await(player2Notices, notice -> notice.type() == NoticeType.CONNECTED);

        ServerResponse created = host.sendCommand(CommandType.CREATE_SESSION, Map.of(
                "hostPlayerId", "host",
                "playerName", "Host",
                "maxPlayers", 2
        )).get(3, TimeUnit.SECONDS);
        assertTrue(created.accepted());
        String sessionId = text(created.payload(), "sessionId");
        String roomCode = text(created.payload(), "roomCode");
        String hostToken = text(created.payload(), "playerToken");
        assertNotNull(sessionId);
        assertNotNull(roomCode);
        assertNotNull(hostToken);
        assertEquals("host", created.payload().get("hostPlayerId"));
        assertEquals(2, created.payload().get("maxPlayers"));

        ServerResponse invalidJoin = player2.sendCommand(CommandType.JOIN_SESSION, Map.of(
                "roomCode", "NOPE",
                "playerId", "player2",
                "playerName", "Player 2"
        )).get(3, TimeUnit.SECONDS);
        assertFalse(invalidJoin.accepted());
        assertTrue(host.isConnected());
        assertTrue(player2.isConnected());

        ServerResponse joined = player2.sendCommand(CommandType.JOIN_SESSION, Map.of(
                "roomCode", roomCode,
                "playerId", "player2",
                "playerName", "Player 2"
        )).get(3, TimeUnit.SECONDS);
        assertTrue(joined.accepted());
        String player2Token = text(joined.payload(), "playerToken");
        assertNotNull(player2Token);
        await(hostUpdates, update -> sessionId.equals(text(update.snapshot(), "sessionId"))
                && players(update).size() == 2);
        await(player2Updates, update -> sessionId.equals(text(update.snapshot(), "sessionId"))
                && players(update).size() == 2);

        ServerResponse started = host.sendCommand(CommandType.START_GAME, Map.of(
                "sessionId", sessionId,
                "playerId", "host",
                "playerToken", hostToken
        )).get(3, TimeUnit.SECONDS);
        assertTrue(started.accepted());

        ServerUpdate hostActive = await(hostUpdates, activeUpdate(sessionId));
        ServerUpdate player2Active = await(player2Updates, activeUpdate(sessionId));
        assertNotNull(hostActive);
        assertEquals("ACTIVE", hostActive.snapshot().get("status"));
        assertNotNull(player2Active);
        assertEquals("ACTIVE", player2Active.snapshot().get("status"));
        assertEquals("host", hostActive.snapshot().get("hostPlayerId"));
        assertEquals(2, hostActive.snapshot().get("maxPlayers"));
        assertEquals(7, currentPlayerHand(hostActive).size());
        assertEquals(5, currentPlayerHand(player2Active).size());
        assertNotEquals(currentPlayerHand(hostActive), currentPlayerHand(player2Active));

        player2.close();
        GameClient rejoinedPlayer2 = connectedClient();
        List<ServerUpdate> rejoinedUpdates = updates(rejoinedPlayer2);
        ServerResponse rejoined = rejoinedPlayer2.sendCommand(CommandType.REJOIN_SESSION, Map.of(
                "sessionIdentity", sessionId,
                "playerId", "player2",
                "playerToken", player2Token
        )).get(3, TimeUnit.SECONDS);
        assertTrue(rejoined.accepted());
        assertEquals(player2Token, rejoined.payload().get("playerToken"));

        ServerUpdate rejoinedState = await(rejoinedUpdates, activeUpdate(sessionId));
        assertNotNull(rejoinedState);
        assertEquals("ACTIVE", rejoinedState.snapshot().get("status"));
        assertEquals(5, currentPlayerHand(rejoinedState).size());
    }

    @Test
    void serverMarksDisconnectedPlayerForfeitAndBroadcastsResultAfterTimeout() throws Exception {
        MutableClock clock = new MutableClock();
        GameEngine engine = new GameEngine(clock);
        server = new GameServer(new GameController(new GameFacade(engine), new RequestMapper()), DEFAULT_PORT);
        server.startAsync();

        GameClient host = connectedClient();
        List<ServerNotice> hostNotices = notices(host);
        GameClient player2 = connectedClient();
        List<ServerNotice> player2Notices = notices(player2);
        List<ServerUpdate> hostUpdates = updates(host);
        await(hostNotices, notice -> notice.type() == NoticeType.CONNECTED);
        await(player2Notices, notice -> notice.type() == NoticeType.CONNECTED);

        ServerResponse created = host.sendCommand(CommandType.CREATE_SESSION, Map.of(
                "hostPlayerId", "host",
                "playerName", "Host",
                "maxPlayers", 2
        )).get(3, TimeUnit.SECONDS);
        String sessionId = text(created.payload(), "sessionId");
        String roomCode = text(created.payload(), "roomCode");
        String hostToken = text(created.payload(), "playerToken");
        assertTrue(created.accepted());

        assertNotNull(roomCode);
        ServerResponse joined = player2.sendCommand(CommandType.JOIN_SESSION, Map.of(
                "roomCode", roomCode,
                "playerId", "player2",
                "playerName", "Player 2"
        )).get(3, TimeUnit.SECONDS);
        assertTrue(joined.accepted());
        assertNotNull(sessionId);
        assertNotNull(hostToken);
        assertTrue(host.sendCommand(CommandType.START_GAME, Map.of(
                "sessionId", sessionId,
                "playerId", "host",
                "playerToken", hostToken
        )).get(3, TimeUnit.SECONDS).accepted());
        await(hostUpdates, activeUpdate(sessionId));

        player2.close();
        await(hostUpdates, update -> sessionId.equals(text(update.snapshot(), "sessionId"))
                && playerHasStatus(update, "player2", "DISCONNECTED"));

        clock.advance(Duration.ofSeconds(91));
        ServerUpdate finished = await(hostUpdates, update -> sessionId.equals(text(update.snapshot(), "sessionId"))
                && "FINISHED".equals(update.snapshot().get("status")));

        assertNotNull(finished);
        assertEquals("host", finished.snapshot().get("winnerPlayerId"));
        assertEquals(List.of("player2"), finished.snapshot().get("forfeitedPlayerIds"));
        assertEquals("DEFEATED", player(finished, "player2").get("playerStatus"));
    }

    @Test
    void serverRejectsCommandsWhenPlayerTokenIsMissingOrWrong() throws Exception {
        server = new AppConfig().gameServer(DEFAULT_PORT);
        server.startAsync();

        GameClient host = connectedClient();
        GameClient player2 = connectedClient();

        ServerResponse created = host.sendCommand(CommandType.CREATE_SESSION, Map.of(
                "hostPlayerId", "host",
                "playerName", "Host",
                "maxPlayers", 2
        )).get(3, TimeUnit.SECONDS);
        assertTrue(created.accepted());
        String sessionId = text(created.payload(), "sessionId");
        String roomCode = text(created.payload(), "roomCode");
        String hostToken = text(created.payload(), "playerToken");
        assertNotNull(sessionId);
        assertNotNull(roomCode);
        assertNotNull(hostToken);

        ServerResponse missingToken = host.sendCommand(CommandType.START_GAME, Map.of(
                "sessionId", sessionId,
                "playerId", "host"
        )).get(3, TimeUnit.SECONDS);
        assertFalse(missingToken.accepted());
        assertEquals("AUTH_REQUIRED", missingToken.code());

        ServerResponse joined = player2.sendCommand(CommandType.JOIN_SESSION, Map.of(
                "roomCode", roomCode,
                "playerId", "player2",
                "playerName", "Player 2"
        )).get(3, TimeUnit.SECONDS);
        assertTrue(joined.accepted());
        String player2Token = text(joined.payload(), "playerToken");
        assertNotNull(player2Token);

        ServerResponse spoofedHost = player2.sendCommand(CommandType.START_GAME, Map.of(
                "sessionId", sessionId,
                "playerId", "host",
                "playerToken", player2Token
        )).get(3, TimeUnit.SECONDS);
        assertFalse(spoofedHost.accepted());
        assertEquals("UNAUTHORIZED_PLAYER", spoofedHost.code());

        ServerResponse legitimateStart = host.sendCommand(CommandType.START_GAME, withToken(Map.of(
                "sessionId", sessionId,
                "playerId", "host"
        ), hostToken)).get(3, TimeUnit.SECONDS);
        assertTrue(legitimateStart.accepted());
    }

    private GameClient connectedClient() throws IOException {
        GameClient client = new GameClient();
        clients.add(client);
        client.connect("127.0.0.1", server.port());
        return client;
    }

    private List<ServerNotice> notices(GameClient client) {
        List<ServerNotice> notices = new CopyOnWriteArrayList<>();
        client.addNoticeListener(notices::add);
        return notices;
    }

    private List<ServerUpdate> updates(GameClient client) {
        List<ServerUpdate> updates = new CopyOnWriteArrayList<>();
        client.addUpdateListener(updates::add);
        return updates;
    }

    private Map<String, Object> withToken(Map<String, Object> payload, String playerToken) {
        Map<String, Object> copy = new LinkedHashMap<>(payload);
        copy.put("playerToken", playerToken);
        return copy;
    }

    private Predicate<ServerUpdate> activeUpdate(String sessionId) {
        return update -> sessionId.equals(text(update.snapshot(), "sessionId"))
                && "ACTIVE".equals(update.snapshot().get("status"));
    }

    private <T> T await(List<T> values, Predicate<T> predicate) throws InterruptedException {
        long deadline = System.nanoTime() + Duration.ofSeconds(3).toNanos();
        while (System.nanoTime() < deadline) {
            for (T value : values) {
                if (predicate.test(value)) {
                    return value;
                }
            }
            Thread.sleep(10);
        }
        fail("Timed out waiting for matching network event");
        return null;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> players(ServerUpdate update) {
        return (List<Map<String, Object>>) update.snapshot().get("players");
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> player(ServerUpdate update, String playerId) {
        return ((List<Map<String, Object>>) update.snapshot().get("players")).stream()
                .filter(player -> playerId.equals(player.get("playerId")))
                .findFirst()
                .orElseThrow();
    }

    @SuppressWarnings("unchecked")
    private boolean playerHasStatus(ServerUpdate update, String playerId, String status) {
        return ((List<Map<String, Object>>) update.snapshot().get("players")).stream()
                .anyMatch(player -> playerId.equals(player.get("playerId"))
                        && status.equals(player.get("playerStatus")));
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> currentPlayerHand(ServerUpdate update) {
        return (List<Map<String, Object>>) update.snapshot().get("currentPlayerHand");
    }

    private String text(Map<String, Object> data, String key) {
        Object value = data.get(key);
        return value == null ? null : value.toString();
    }

    private static final class MutableClock extends Clock {
        private Instant instant = Instant.parse("2026-05-28T00:00:00Z");

        void advance(Duration duration) {
            instant = instant.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneId.of("UTC");
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
