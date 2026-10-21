package ie.ucd.bdic.group6.ui;

import ie.ucd.bdic.group6.core.engine.PlayerPhase;
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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UiSnapshotTest {

    @Test
    void exposesEngineOwnedPlayerPhase() {
        PlayerStateSnapshot player = new PlayerStateSnapshot(
                "host",
                "Host",
                "CONNECTED",
                "DISCARDING",
                true,
                "",
                false,
                8,
                0,
                List.of(),
                List.of());
        UiSnapshot snapshot = UiSnapshot.from(new SessionSnapshot(
                "s1", "ABC123", "host", 2, "ACTIVE", "host", 3, 0, "", 20, 0,
                List.of(), List.of(), List.of(), List.of(), List.of(player), List.of()));

        assertEquals(PlayerPhase.DISCARDING, snapshot.playerPhase("host"));
        assertEquals(PlayerPhase.WAITING, snapshot.playerPhase("missing"));
    }

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
                0,
                1,
                "BROWN",
                2,
                List.of(),
                "",
                List.of()
        );
        CardSnapshot guestProperty = new CardSnapshot(
                "guest-property",
                "PROPERTY",
                "Oriental Avenue",
                0,
                1,
                "LIGHT_BLUE",
                3,
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
                        player("guest", "Guest", List.of(),
                                List.of(new PropertyRowSnapshot(
                                        "guest-row",
                                        "LIGHT_BLUE",
                                        false,
                                        1,
                                        false,
                                        false,
                                        List.of(guestProperty)
                                )))
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
        assertTrue(snapshot.canRespondToTrade("host"));
        assertFalse(snapshot.canRespondToTrade("guest"));
        assertEquals("2026-06-06T00:01:00Z", snapshot.pendingActionsAwaiting("host").getFirst().createdAt());
        assertTrue(snapshot.canProposeTrade("host"));
        assertEquals("1/2 cards", snapshot.player("host").orElseThrow().propertyRows().getFirst().progressLabel());
    }

    @Test
    void filtersGameScreenChoicesToActionableCardsAndRows() {
        CardSnapshot money = card("money-1", "MONEY", "$1M");
        CardSnapshot brown1 = propertyCard("brown-1", "BROWN", 1, 2);
        CardSnapshot brown2 = propertyCard("brown-2", "BROWN", 1, 2);
        CardSnapshot utility1 = propertyCard("utility-1", "UTILITY", 2, 2);
        CardSnapshot utility2 = propertyCard("utility-2", "UTILITY", 2, 2);
        CardSnapshot red = propertyCard("red-1", "RED", 3, 3);
        CardSnapshot rainbow = new CardSnapshot(
                "rainbow",
                "PROPERTY_WILD",
                "Rainbow Wild",
                0,
                0,
                "",
                0,
                List.of("BROWN", "RED"),
                "",
                List.of()
        );
        CardSnapshot rent = new CardSnapshot(
                "rent",
                "ACTION",
                "Rent",
                1,
                1,
                "",
                0,
                List.of(),
                "RENT_BROWN_OR_LIGHT_BLUE",
                List.of("BROWN", "LIGHT_BLUE")
        );
        UiSnapshot snapshot = UiSnapshot.from(new SessionSnapshot(
                "s1",
                "ABC123",
                "host",
                2,
                "ACTIVE",
                "host",
                0,
                3,
                "",
                20,
                0,
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(player("host", "Host", List.of(money), List.of(
                        new PropertyRowSnapshot("brown-row", "BROWN", true, 3, false, false, List.of(brown1, brown2)),
                        new PropertyRowSnapshot("utility-row", "UTILITY", true, 2, false, false, List.of(utility1, utility2)),
                        new PropertyRowSnapshot("red-row", "RED", false, 2, false, false, List.of(red, rainbow))
                ))),
                List.of(rent)
        ));
        UiSnapshot.PlayerView host = snapshot.player("host").orElseThrow();
        UiSnapshot.CardView rentView = snapshot.currentPlayerHand().getFirst();

        assertEquals(List.of("money-1", "brown-1", "brown-2", "utility-1", "utility-2", "red-1"),
                host.paymentCards().stream().map(UiSnapshot.CardView::id).toList());
        assertEquals(List.of("red-1", "rainbow"),
                host.stealablePropertyCards().stream().map(UiSnapshot.CardView::id).toList());
        assertEquals(List.of("brown-row"),
                host.improvableRows("HOUSE").stream().map(UiSnapshot.PropertyRowView::rowId).toList());
        assertTrue(host.improvableRows("HOTEL").isEmpty());
        assertEquals(List.of("BROWN"), host.rentableColorsFor(rentView));
    }

    @Test
    void tradeChoicesTreatOnlyActualMoneyCardsAsBankMoney() {
        CardSnapshot money = card("money-1", "MONEY", "$1M");
        CardSnapshot bankedAction = card("banked-action", "ACTION", "Pass Go");
        CardSnapshot bankedWild = new CardSnapshot(
                "banked-wild",
                "PROPERTY_WILD",
                "Dual Wild",
                1,
                0,
                "",
                0,
                List.of("BROWN", "LIGHT_BLUE"),
                "",
                List.of()
        );
        CardSnapshot guestProperty = propertyCard("guest-property", "RED", 3, 3);
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
                0,
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(
                        player("host", "Host", List.of(money, bankedAction, bankedWild), List.of()),
                        player("guest", "Guest", List.of(), List.of(
                                new PropertyRowSnapshot(
                                        "guest-row",
                                        "RED",
                                        false,
                                        3,
                                        false,
                                        false,
                                        List.of(guestProperty)
                                )))
                ),
                List.of()
        ));

        UiSnapshot.PlayerView host = snapshot.player("host").orElseThrow();
        UiSnapshot.PlayerView guest = snapshot.player("guest").orElseThrow();

        assertEquals(List.of("money-1"),
                host.supportedTradeRequestsFor(guest.propertyCards().getFirst(), guest).stream()
                        .map(UiSnapshot.CardView::id)
                        .toList());
        assertTrue(host.canTradeWith(guest));
    }

    @Test
    void tradeResponseRequiresTargetPlayersTurn() {
        UiSnapshot targetOffTurn = UiSnapshot.from(snapshotWithPendingTrade("host", "guest", "host"));
        UiSnapshot targetTurn = UiSnapshot.from(snapshotWithPendingTrade("guest", "guest", "host"));

        assertFalse(targetOffTurn.canRespondToTrade("guest"));
        assertTrue(targetTurn.canRespondToTrade("guest"));
        assertFalse(targetOffTurn.canRespondToTrade("host"));
    }

    private SessionSnapshot snapshotWithPendingTrade(String activePlayerId, String targetPlayerId, String proposerPlayerId) {
        return new SessionSnapshot(
                "s1",
                "ABC123",
                "host",
                2,
                "ACTIVE",
                activePlayerId,
                0,
                0,
                "",
                20,
                0,
                List.of(),
                List.of(new PendingTradeSnapshot(
                        "trade-1",
                        proposerPlayerId,
                        targetPlayerId,
                        List.of(),
                        List.of(),
                        "PENDING",
                        "2026-06-06T00:00:00Z",
                        ""
                )),
                List.of(),
                List.of(),
                List.of(
                        player("host", "Host", List.of(card("host-money", "MONEY", "$1M")), List.of()),
                        player("guest", "Guest", List.of(card("guest-money", "MONEY", "$1M")), List.of())
                ),
                List.of()
        );
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

    private CardSnapshot propertyCard(String id, String color, int valueM, int requiredToComplete) {
        return new CardSnapshot(
                id,
                "PROPERTY",
                id,
                0,
                valueM,
                color,
                requiredToComplete,
                List.of(),
                "",
                List.of()
        );
    }
}
