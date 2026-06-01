package ie.ucd.bdic.group6.integration;

import ie.ucd.bdic.group6.app.AppConfig;
import ie.ucd.bdic.group6.command.CommandType;
import ie.ucd.bdic.group6.controller.GameController;
import ie.ucd.bdic.group6.controller.mapper.RequestMapper;
import ie.ucd.bdic.group6.core.engine.GameEngine;
import ie.ucd.bdic.group6.facade.GameFacade;
import ie.ucd.bdic.group6.network.client.GameClient;
import ie.ucd.bdic.group6.network.protocol.ServerNotice;
import ie.ucd.bdic.group6.network.protocol.ServerResult;
import ie.ucd.bdic.group6.network.protocol.StateUpdate;
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
        server = new AppConfig().gameServer(0);
        server.startAsync();
        assertTrue(server.isRunning());
        assertTrue(server.port() > 0);

        GameClient host = connectedClient();
        GameClient player2 = connectedClient();
        List<ServerNotice> hostNotices = notices(host);
        List<ServerNotice> player2Notices = notices(player2);
        List<StateUpdate> hostUpdates = updates(host);
        List<StateUpdate> player2Updates = updates(player2);

        await(hostNotices, notice -> notice.type().equals("CONNECTED"));
        await(player2Notices, notice -> notice.type().equals("CONNECTED"));

        ServerResult created = host.sendCommand(CommandType.CREATE_SESSION, Map.of(
                "hostPlayerId", "host",
                "playerName", "Host",
                "maxPlayers", 2
        )).get(3, TimeUnit.SECONDS);
        assertTrue(created.success());
        String sessionId = text(created.data(), "sessionId");
        String sessionCode = text(created.data(), "sessionCode");
        String hostToken = text(created.data(), "playerToken");
        assertNotNull(sessionId);
        assertNotNull(sessionCode);
        assertNotNull(hostToken);
        assertEquals("host", created.data().get("hostPlayerId"));
        assertEquals(2, created.data().get("maxPlayers"));

        ServerResult invalidJoin = player2.sendCommand(CommandType.JOIN_SESSION, Map.of(
                "sessionCode", "NOPE",
                "playerId", "player2",
                "playerName", "Player 2"
        )).get(3, TimeUnit.SECONDS);
        assertFalse(invalidJoin.success());
        assertTrue(host.isConnected());
        assertTrue(player2.isConnected());

        ServerResult joined = player2.sendCommand(CommandType.JOIN_SESSION, Map.of(
                "sessionCode", sessionCode,
                "playerId", "player2",
                "playerName", "Player 2"
        )).get(3, TimeUnit.SECONDS);
        assertTrue(joined.success());
        String player2Token = text(joined.data(), "playerToken");
        assertNotNull(player2Token);
        await(hostUpdates, update -> update.sessionId().equals(sessionId)
                && players(update).size() == 2);
        await(player2Updates, update -> update.sessionId().equals(sessionId)
                && players(update).size() == 2);

        ServerResult started = host.sendCommand(CommandType.START_GAME, Map.of(
                "sessionId", sessionId,
                "playerId", "host",
                "playerToken", hostToken
        )).get(3, TimeUnit.SECONDS);
        assertTrue(started.success());

        StateUpdate hostActive = await(hostUpdates, activeUpdate(sessionId, "host"));
        StateUpdate player2Active = await(player2Updates, activeUpdate(sessionId, "player2"));
        assertNotNull(hostActive);
        assertEquals("ACTIVE", hostActive.data().get("status"));
        assertNotNull(player2Active);
        assertEquals("ACTIVE", player2Active.data().get("status"));
        assertEquals("host", hostActive.data().get("hostPlayerId"));
        assertEquals(2, hostActive.data().get("maxPlayers"));
        assertEquals(7, currentPlayerHand(hostActive).size());
        assertEquals(5, currentPlayerHand(player2Active).size());
        assertNotEquals(currentPlayerHand(hostActive), currentPlayerHand(player2Active));

        player2.close();
        GameClient rejoinedPlayer2 = connectedClient();
        List<StateUpdate> rejoinedUpdates = updates(rejoinedPlayer2);
        ServerResult rejoined = rejoinedPlayer2.sendCommand(CommandType.REJOIN_SESSION, Map.of(
                "sessionIdentity", sessionId,
                "playerId", "player2",
                "playerToken", player2Token
        )).get(3, TimeUnit.SECONDS);
        assertTrue(rejoined.success());
        assertEquals(player2Token, rejoined.data().get("playerToken"));

        StateUpdate rejoinedState = await(rejoinedUpdates, activeUpdate(sessionId, "player2"));
        assertNotNull(rejoinedState);
        assertEquals("ACTIVE", rejoinedState.data().get("status"));
        assertEquals(5, currentPlayerHand(rejoinedState).size());
    }

    @Test
    void serverMarksDisconnectedPlayerForfeitAndBroadcastsResultAfterTimeout() throws Exception {
        MutableClock clock = new MutableClock();
        GameEngine engine = new GameEngine(clock);
        server = new GameServer(new GameController(new GameFacade(engine), new RequestMapper()), 0);
        server.startAsync();

        GameClient host = connectedClient();
        List<ServerNotice> hostNotices = notices(host);
        GameClient player2 = connectedClient();
        List<ServerNotice> player2Notices = notices(player2);
        List<StateUpdate> hostUpdates = updates(host);
        await(hostNotices, notice -> notice.type().equals("CONNECTED"));
        await(player2Notices, notice -> notice.type().equals("CONNECTED"));

        ServerResult created = host.sendCommand(CommandType.CREATE_SESSION, Map.of(
                "hostPlayerId", "host",
                "playerName", "Host",
                "maxPlayers", 2
        )).get(3, TimeUnit.SECONDS);
        String sessionId = text(created.data(), "sessionId");
        String sessionCode = text(created.data(), "sessionCode");
        String hostToken = text(created.data(), "playerToken");
        assertTrue(created.success());

        assertNotNull(sessionCode);
        ServerResult joined = player2.sendCommand(CommandType.JOIN_SESSION, Map.of(
                "sessionCode", sessionCode,
                "playerId", "player2",
                "playerName", "Player 2"
        )).get(3, TimeUnit.SECONDS);
        assertTrue(joined.success());
        assertNotNull(sessionId);
        assertNotNull(hostToken);
        assertTrue(host.sendCommand(CommandType.START_GAME, Map.of(
                "sessionId", sessionId,
                "playerId", "host",
                "playerToken", hostToken
        )).get(3, TimeUnit.SECONDS).success());
        await(hostUpdates, activeUpdate(sessionId, "host"));

        player2.close();
        await(hostUpdates, update -> update.sessionId().equals(sessionId)
                && playerHasStatus(update, "player2", "DISCONNECTED"));

        clock.advance(Duration.ofSeconds(91));
        StateUpdate finished = await(hostUpdates, update -> update.sessionId().equals(sessionId)
                && "FINISHED".equals(update.data().get("status")));

        assertNotNull(finished);
        assertEquals("host", finished.data().get("winnerPlayerId"));
        assertEquals(List.of("player2"), finished.data().get("forfeitedPlayerIds"));
        assertEquals("DEFEATED", player(finished, "player2").get("playerStatus"));
    }

    @Test
    void serverRejectsCommandsWhenPlayerTokenIsMissingOrWrong() throws Exception {
        server = new AppConfig().gameServer(0);
        server.startAsync();

        GameClient host = connectedClient();
        GameClient player2 = connectedClient();

        ServerResult created = host.sendCommand(CommandType.CREATE_SESSION, Map.of(
                "hostPlayerId", "host",
                "playerName", "Host",
                "maxPlayers", 2
        )).get(3, TimeUnit.SECONDS);
        assertTrue(created.success());
        String sessionId = text(created.data(), "sessionId");
        String sessionCode = text(created.data(), "sessionCode");
        String hostToken = text(created.data(), "playerToken");
        assertNotNull(sessionId);
        assertNotNull(sessionCode);
        assertNotNull(hostToken);

        ServerResult missingToken = host.sendCommand(CommandType.START_GAME, Map.of(
                "sessionId", sessionId,
                "playerId", "host"
        )).get(3, TimeUnit.SECONDS);
        assertFalse(missingToken.success());
        assertEquals("AUTH_REQUIRED", missingToken.code());

        ServerResult joined = player2.sendCommand(CommandType.JOIN_SESSION, Map.of(
                "sessionCode", sessionCode,
                "playerId", "player2",
                "playerName", "Player 2"
        )).get(3, TimeUnit.SECONDS);
        assertTrue(joined.success());
        String player2Token = text(joined.data(), "playerToken");
        assertNotNull(player2Token);

        ServerResult spoofedHost = player2.sendCommand(CommandType.START_GAME, Map.of(
                "sessionId", sessionId,
                "playerId", "host",
                "playerToken", player2Token
        )).get(3, TimeUnit.SECONDS);
        assertFalse(spoofedHost.success());
        assertEquals("UNAUTHORIZED_PLAYER", spoofedHost.code());

        ServerResult legitimateStart = host.sendCommand(CommandType.START_GAME, withToken(Map.of(
                "sessionId", sessionId,
                "playerId", "host"
        ), hostToken)).get(3, TimeUnit.SECONDS);
        assertTrue(legitimateStart.success());
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

    private List<StateUpdate> updates(GameClient client) {
        List<StateUpdate> updates = new CopyOnWriteArrayList<>();
        client.addStateUpdateListener(updates::add);
        return updates;
    }

    private Map<String, Object> withToken(Map<String, Object> payload, String playerToken) {
        Map<String, Object> copy = new LinkedHashMap<>(payload);
        copy.put("playerToken", playerToken);
        return copy;
    }

    private Predicate<StateUpdate> activeUpdate(String sessionId, String playerId) {
        return update -> update.sessionId().equals(sessionId)
                && update.playerId().equals(playerId)
                && "ACTIVE".equals(update.data().get("status"));
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
    private List<Map<String, Object>> players(StateUpdate update) {
        return (List<Map<String, Object>>) update.data().get("players");
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> player(StateUpdate update, String playerId) {
        return ((List<Map<String, Object>>) update.data().get("players")).stream()
                .filter(player -> playerId.equals(player.get("playerId")))
                .findFirst()
                .orElseThrow();
    }

    @SuppressWarnings("unchecked")
    private boolean playerHasStatus(StateUpdate update, String playerId, String status) {
        return ((List<Map<String, Object>>) update.data().get("players")).stream()
                .anyMatch(player -> playerId.equals(player.get("playerId"))
                        && status.equals(player.get("playerStatus")));
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> currentPlayerHand(StateUpdate update) {
        return (List<Map<String, Object>>) update.data().get("currentPlayerHand");
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
