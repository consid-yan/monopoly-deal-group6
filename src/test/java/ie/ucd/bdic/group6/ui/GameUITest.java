package ie.ucd.bdic.group6.ui;

import ie.ucd.bdic.group6.core.snapshot.PendingDebtSnapshot;
import ie.ucd.bdic.group6.core.snapshot.PlayerStateSnapshot;
import ie.ucd.bdic.group6.core.snapshot.SessionSnapshot;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class GameUITest {

    @Test
    void clickingAlreadySelectedCardRequestsPlayOnOwnTurn() {
        assertEquals(GameUI.HandCardClickAction.PLAY_SELECTED,
                GameUI.resolveHandCardClick("card-1", "card-1", true));
    }

    @Test
    void clickingAlreadySelectedCardOnlySelectsWhenWaitingForTurn() {
        assertEquals(GameUI.HandCardClickAction.SELECT,
                GameUI.resolveHandCardClick("card-1", "card-1", false));
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

    private UiSnapshot snapshot(String activePlayerId, List<PendingDebtSnapshot> pendingDebts) {
        return UiSnapshot.from(new SessionSnapshot(
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
                pendingDebts,
                List.of(),
                List.of(),
                List.of(),
                List.of(
                        player("host", "Host"),
                        player("guest", "Guest")
                ),
                List.of()
        ));
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
