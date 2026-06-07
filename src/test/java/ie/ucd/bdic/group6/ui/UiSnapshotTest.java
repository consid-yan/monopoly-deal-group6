package ie.ucd.bdic.group6.ui;

import ie.ucd.bdic.group6.core.snapshot.CardSnapshot;
import ie.ucd.bdic.group6.core.snapshot.PendingActionSnapshot;
import ie.ucd.bdic.group6.core.snapshot.PendingDebtSnapshot;
import ie.ucd.bdic.group6.core.snapshot.PendingTradeSnapshot;
import ie.ucd.bdic.group6.core.snapshot.PlayerStateSnapshot;
import ie.ucd.bdic.group6.core.snapshot.PropertyRowSnapshot;
import ie.ucd.bdic.group6.core.snapshot.SessionSnapshot;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UiSnapshotTest {

    @Test
    void parsesLobbyFieldsNeededByJavaFxScreens() {
        UiSnapshot snapshot = UiSnapshot.from(new SessionSnapshot(
                "s1",
                "ABC123",
                "host",
                5,
                "LOBBY",
                "",
                0,
                0,
                "",
                0,
                0,
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(player("host", "Host", List.of(), List.of())),
                List.of()
        ));

        assertEquals("s1", snapshot.sessionId());
        assertEquals("ABC123", snapshot.roomCode());
        assertEquals("host", snapshot.hostPlayerId());
        assertEquals(5, snapshot.maxPlayers());
        assertEquals("Host", snapshot.playerName("host"));
    }

    @Test
    void keepsCurrentPlayerHandSeparateFromOpponentPublicCards() {
        CardSnapshot privateCard = card("private-1", "MONEY", "$1M");
        CardSnapshot publicCard = card("public-1", "MONEY", "$2M");
        UiSnapshot snapshot = UiSnapshot.from(new SessionSnapshot(
                "s1",
                "ABC123",
                "host",
                2,
                "ACTIVE",
                "",
                0,
                0,
                "",
                0,
                0,
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(
                        player("host", "Host", List.of(publicCard), List.of()),
                        player("guest", "Guest", List.of(), List.of())
                ),
                List.of(privateCard)
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
        CardSnapshot propertyCard = new CardSnapshot(
                "property-1",
                "PROPERTY",
                "Baltic Avenue",
                1,
                0,
                "BROWN",
                2,
                List.of(),
                "",
                List.of()
        );
        UiSnapshot snapshot = UiSnapshot.from(new SessionSnapshot(
                "s1",
                "ABC123",
                "host",
                2,
                "ACTIVE",
                "host",
                0,
                0,
                "",
                20,
                3,
                List.of(new PendingDebtSnapshot(
                        "debt-1",
                        "guest",
                        "host",
                        2,
                        "",
                        ""
                )),
                List.of(new PendingTradeSnapshot(
                        "trade-1",
                        "guest",
                        "host",
                        List.of(),
                        List.of(),
                        "PENDING",
                        "2026-06-06T00:00:00Z",
                        ""
                )),
                List.of(new PendingActionSnapshot(
                        "action-1",
                        "guest",
                        "DEBT_COLLECTOR",
                        "action-card-1",
                        "",
                        "",
                        5,
                        "",
                        "",
                        "",
                        "",
                        List.of("host"),
                        Map.of("host", "PENDING"),
                        "2026-06-06T00:01:00Z"
                )),
                List.of(),
                List.of(
                        player("host", "Host", List.of(card("money-1", "MONEY", "$1M")),
                                List.of(new PropertyRowSnapshot(
                                        "row-1",
                                        "BROWN",
                                        false,
                                        1,
                                        false,
                                        false,
                                        List.of(propertyCard)
                                ))),
                        player("guest", "Guest", List.of(), List.of())
                ),
                List.of(
                        card("h1", "MONEY", "$1M"),
                        card("h2", "MONEY", "$1M"),
                        card("h3", "MONEY", "$1M"),
                        card("h4", "MONEY", "$1M"),
                        card("h5", "MONEY", "$1M"),
                        card("h6", "MONEY", "$1M"),
                        card("h7", "MONEY", "$1M"),
                        card("h8", "MONEY", "$1M")
                )
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

    private PlayerStateSnapshot player(
            String playerId,
            String name,
            List<CardSnapshot> bankCards,
            List<PropertyRowSnapshot> propertyRows) {
        return new PlayerStateSnapshot(
                playerId,
                name,
                "CONNECTED",
                true,
                "",
                false,
                0,
                0,
                bankCards,
                propertyRows
        );
    }

    private CardSnapshot card(String id, String type, String displayName) {
        return new CardSnapshot(
                id,
                type,
                displayName,
                1,
                0,
                "",
                0,
                List.of(),
                "",
                List.of()
        );
    }
}
