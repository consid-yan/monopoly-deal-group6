package ie.ucd.bdic.group6.ui;

import ie.ucd.bdic.group6.core.engine.PlayerPhase;
import ie.ucd.bdic.group6.core.snapshot.CardSnapshot;
import ie.ucd.bdic.group6.core.snapshot.PendingDebtSnapshot;
import ie.ucd.bdic.group6.core.snapshot.PendingActionSnapshot;
import ie.ucd.bdic.group6.core.snapshot.PlayerStateSnapshot;
import ie.ucd.bdic.group6.core.snapshot.SessionSnapshot;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GameUITest {

    @Test
    void clickingAlreadySelectedCardRequestsPlayOnOwnTurn() {
        assertEquals(GameUI.HandCardClickAction.PLAY_SELECTED,
                GameUI.resolveHandCardClick("card-1", "card-1", PlayerPhase.ACTING));
    }

    @Test
    void clickingAlreadySelectedCardDiscardsDuringDiscardPhase() {
        assertEquals(GameUI.HandCardClickAction.DISCARD_SELECTED,
                GameUI.resolveHandCardClick("card-1", "card-1", PlayerPhase.DISCARDING));
    }

    @Test
    void handCardClicksAreIgnoredWhileWaitingOrResponding() {
        assertEquals(GameUI.HandCardClickAction.IGNORE,
                GameUI.resolveHandCardClick("card-1", "card-1", PlayerPhase.WAITING));
        assertEquals(GameUI.HandCardClickAction.IGNORE,
                GameUI.resolveHandCardClick("card-1", "card-1", PlayerPhase.RESPONDING));
    }

    @Test
    void firstHandCardClickSelectsWhileActingOrDiscarding() {
        assertEquals(GameUI.HandCardClickAction.SELECT,
                GameUI.resolveHandCardClick("card-1", null, PlayerPhase.ACTING));
        assertEquals(GameUI.HandCardClickAction.SELECT,
                GameUI.resolveHandCardClick("card-1", null, PlayerPhase.DISCARDING));
    }

    @Test
    void submittedDebtIsNotPromptedAgainWhileWaitingForServerResponse() {
        assertFalse(GameUI.shouldPromptDebt("debt-1", Set.of("debt-1")));
    }

    @Test
    void activityLinesUsePlayerLanguageWhenNothingIsPending() {
        UiSnapshot snapshot = snapshot("host", List.of());

        assertEquals(List.of("No pending actions."),
                GameUI.buildActivityLines(snapshot, "host"));
    }

    @Test
    void activityLinesDescribePendingDebtWithPlayerNameAndAmount() {
        UiSnapshot snapshot = snapshot("host", List.of(new PendingDebtSnapshot(
                "debt-1",
                "host",
                "guest",
                3,
                "ITS_MY_BIRTHDAY",
                "birthday-card"
        )));

        assertEquals(List.of("Guest needs to pay $3M."),
                GameUI.buildActivityLines(snapshot, "host"));
    }

    @Test
    void activityLinesDescribePendingActionWithWaitingPlayers() {
        UiSnapshot snapshot = snapshot("host", List.of(), List.of(new PendingActionSnapshot(
                "action-1",
                "host",
                "DEBT_COLLECTOR",
                "debt-collector-card",
                "",
                "",
                5,
                "",
                "",
                "",
                "",
                List.of("guest"),
                Map.of("guest", "PENDING"),
                "2026-06-06T00:01:00Z"
        )));

        assertEquals(List.of("Waiting for Guest to respond to Debt Collector."),
                GameUI.buildActivityLines(snapshot, "host"));
    }

    @Test
    void paymentCheckBoxesHideTheMarkUntilSelected() throws Exception {
        String css = new String(
                Objects.requireNonNull(getClass().getResourceAsStream("/css/style.css")).readAllBytes(),
                java.nio.charset.StandardCharsets.UTF_8);

        assertTrue(css.matches("(?s).*\\.app-dialog-check-box\\s+\\.mark\\s*\\{[^}]*"
                        + "-fx-background-color\\s*:\\s*transparent\\s*;[^}]*}.*"),
                "Payment checkboxes need an explicit unchecked mark style");
    }

    @Test
    void propertyRowsScrollInsideTheirPanelWithoutCoveringTheFixedBankRow() throws Exception {
        String css = new String(
                Objects.requireNonNull(getClass().getResourceAsStream("/css/style.css")).readAllBytes(),
                java.nio.charset.StandardCharsets.UTF_8);

        assertTrue(css.matches("(?s).*\\.clean-game-board-row\\s*\\{[^}]*"
                        + "-fx-max-height\\s*:\\s*146\\s*;[^}]*}.*"),
                "The bank row must stay fixed while property rows grow");
        assertTrue(css.matches("(?s).*\\.clean-property-panel\\s*\\{[^}]*"
                        + "-fx-min-height\\s*:\\s*0\\s*;[^}]*}.*"),
                "The property panel must be allowed to shrink into its scroll viewport");
        assertTrue(css.matches("(?s).*\\.game-property-scroll\\s*\\{[^}]*"
                        + "-fx-min-height\\s*:\\s*0\\s*;[^}]*}.*"),
                "The property scroll pane must clip overflowing property rows");
    }

    private UiSnapshot snapshot(String activePlayerId, List<PendingDebtSnapshot> pendingDebts) {
        return snapshot(activePlayerId, pendingDebts, List.of());
    }

    private UiSnapshot snapshot(
            String activePlayerId,
            List<PendingDebtSnapshot> pendingDebts,
            List<PendingActionSnapshot> pendingActions) {
        return snapshot(activePlayerId, 0, 0, pendingDebts, pendingActions);
    }

    private UiSnapshot snapshot(
            String activePlayerId,
            int actionsUsed,
            int actionsRemaining,
            List<PendingDebtSnapshot> pendingDebts,
            List<PendingActionSnapshot> pendingActions) {
        return snapshot(activePlayerId, actionsUsed, actionsRemaining, pendingDebts, pendingActions, List.of());
    }

    private UiSnapshot snapshot(
            String activePlayerId,
            int actionsUsed,
            int actionsRemaining,
            List<PendingDebtSnapshot> pendingDebts,
            List<PendingActionSnapshot> pendingActions,
            List<CardSnapshot> hand) {
        return UiSnapshot.from(new SessionSnapshot(
                "s1",
                "ABC123",
                "host",
                2,
                "ACTIVE",
                activePlayerId,
                actionsUsed,
                actionsRemaining,
                "",
                20,
                0,
                pendingDebts,
                List.of(),
                pendingActions,
                List.of(),
                List.of(
                        player("host", "Host"),
                        player("guest", "Guest")
                ),
                hand
        ));
    }

    private CardSnapshot card(String id) {
        return new CardSnapshot(id, "MONEY", id, 1, 1, "", 0, List.of(), "", List.of());
    }

    private PlayerStateSnapshot player(String playerId, String name) {
        return new PlayerStateSnapshot(
                playerId,
                name,
                "CONNECTED",
                true,
                "",
                false,
                0,
                0,
                List.of(),
                List.of()
        );
    }
}
