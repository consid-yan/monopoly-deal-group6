package ie.ucd.bdic.group6.ui;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static java.util.Map.entry;

class UiSnapshotTest {

    @Test
    void parsesLobbyFieldsNeededByJavaFxScreens() {
        UiSnapshot snapshot = UiSnapshot.from(Map.ofEntries(
                entry("sessionId", "s1"),
                entry("sessionCode", "ABC123"),
                entry("hostPlayerId", "host"),
                entry("maxPlayers", 5),
                entry("status", "LOBBY"),
                entry("players", List.of(Map.of(
                        "playerId", "host",
                        "name", "Host",
                        "playerStatus", "CONNECTED",
                        "connected", true,
                        "handCount", 0,
                        "bankTotalValueM", 0,
                        "bankCards", List.of(),
                        "propertyRows", List.of()
                ))),
                entry("currentPlayerHand", List.of()),
                entry("pendingDebts", List.of()),
                entry("pendingTrades", List.of()),
                entry("pendingActions", List.of()),
                entry("forfeitedPlayerIds", List.of())
        ));

        assertEquals("s1", snapshot.sessionId());
        assertEquals("ABC123", snapshot.sessionCode());
        assertEquals("host", snapshot.hostPlayerId());
        assertEquals(5, snapshot.maxPlayers());
        assertEquals("Host", snapshot.playerName("host"));
    }

    @Test
    void keepsCurrentPlayerHandSeparateFromOpponentPublicCards() {
        Map<String, Object> privateCard = card("private-1", "MONEY", "$1M");
        Map<String, Object> publicCard = card("public-1", "MONEY", "$2M");
        UiSnapshot snapshot = UiSnapshot.from(Map.ofEntries(
                entry("sessionId", "s1"),
                entry("sessionCode", "ABC123"),
                entry("hostPlayerId", "host"),
                entry("maxPlayers", 2),
                entry("status", "ACTIVE"),
                entry("players", List.of(
                        player("host", "Host", List.of(publicCard), List.of()),
                        player("guest", "Guest", List.of(), List.of())
                )),
                entry("currentPlayerHand", List.of(privateCard)),
                entry("pendingDebts", List.of()),
                entry("pendingTrades", List.of()),
                entry("pendingActions", List.of()),
                entry("forfeitedPlayerIds", List.of())
        ));

        assertEquals(List.of("private-1"), snapshot.currentPlayerHand().stream()
                .map(UiSnapshot.CardView::id)
                .toList());
        assertTrue(snapshot.card("public-1").isPresent());
        assertTrue(snapshot.card("private-1").isPresent());
        assertEquals(0, snapshot.player("guest").orElseThrow().handCount());
    }

    private Map<String, Object> player(
            String playerId,
            String name,
            List<Map<String, Object>> bankCards,
            List<Map<String, Object>> propertyRows) {
        return Map.of(
                "playerId", playerId,
                "name", name,
                "playerStatus", "CONNECTED",
                "connected", true,
                "handCount", 0,
                "bankTotalValueM", 0,
                "bankCards", bankCards,
                "propertyRows", propertyRows
        );
    }

    private Map<String, Object> card(String id, String type, String displayName) {
        return Map.of(
                "id", id,
                "type", type,
                "displayName", displayName,
                "bankValueM", 1
        );
    }
}
