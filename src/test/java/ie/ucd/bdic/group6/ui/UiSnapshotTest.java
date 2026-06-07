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
                entry("roomCode", "ABC123"),
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
        assertEquals("ABC123", snapshot.roomCode());
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
                entry("roomCode", "ABC123"),
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

    @Test
    void exposesViewerCentricHelpersForGameScreen() {
        Map<String, Object> propertyCard = Map.of(
                "id", "property-1",
                "type", "PROPERTY",
                "displayName", "Baltic Avenue",
                "bankValueM", 1,
                "propertyColor", "BROWN",
                "requiredToComplete", 2
        );
        UiSnapshot snapshot = UiSnapshot.from(Map.ofEntries(
                entry("sessionId", "s1"),
                entry("roomCode", "ABC123"),
                entry("hostPlayerId", "host"),
                entry("maxPlayers", 2),
                entry("status", "ACTIVE"),
                entry("activePlayerId", "host"),
                entry("deckSize", 20),
                entry("discardSize", 3),
                entry("players", List.of(
                        player("host", "Host", List.of(card("money-1", "MONEY", "$1M")),
                                List.of(Map.of(
                                        "rowId", "row-1",
                                        "anchorColor", "BROWN",
                                        "complete", false,
                                        "rentValue", 1,
                                        "hasHouse", false,
                                        "hasHotel", false,
                                        "cards", List.of(propertyCard)
                                ))),
                        player("guest", "Guest", List.of(), List.of())
                )),
                entry("currentPlayerHand", List.of(
                        card("h1", "MONEY", "$1M"),
                        card("h2", "MONEY", "$1M"),
                        card("h3", "MONEY", "$1M"),
                        card("h4", "MONEY", "$1M"),
                        card("h5", "MONEY", "$1M"),
                        card("h6", "MONEY", "$1M"),
                        card("h7", "MONEY", "$1M"),
                        card("h8", "MONEY", "$1M")
                )),
                entry("pendingDebts", List.of(Map.of(
                        "debtId", "debt-1",
                        "creditorPlayerId", "guest",
                        "debtorPlayerId", "host",
                        "amountM", 2
                ))),
                entry("pendingTrades", List.of(Map.of(
                        "tradeId", "trade-1",
                        "proposerPlayerId", "guest",
                        "targetPlayerId", "host",
                        "offeredCardIds", List.of(),
                        "requestedCardIds", List.of(),
                        "status", "PENDING",
                        "createdAt", "2026-06-06T00:00:00Z",
                        "respondedAt", ""
                ))),
                entry("pendingActions", List.of(Map.of(
                        "actionId", "action-1",
                        "actorPlayerId", "guest",
                        "actionType", "DEBT_COLLECTOR",
                        "actionCardId", "action-card-1",
                        "amountM", 5,
                        "targetPlayerIds", List.of("host"),
                        "responses", Map.of("host", "PENDING"),
                        "createdAt", "2026-06-06T00:01:00Z"
                ))),
                entry("forfeitedPlayerIds", List.of())
        ));

        assertTrue(snapshot.isTurnFor("host"));
        assertEquals(1, snapshot.cardsToDiscard());
        assertEquals(1, snapshot.pendingDebtsForDebtor("host").size());
        assertEquals("2026-06-06T00:00:00Z", snapshot.pendingTradesForTarget("host").getFirst().createdAt());
        assertEquals("2026-06-06T00:01:00Z", snapshot.pendingActionsAwaiting("host").getFirst().createdAt());
        assertTrue(snapshot.canProposeTrade("host"));
        assertEquals("1/2 cards", snapshot.player("host").orElseThrow().propertyRows().getFirst().progressLabel());
        assertTrue(snapshot.tableActivityLines().stream().anyMatch(line -> line.contains("Debt")));
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
