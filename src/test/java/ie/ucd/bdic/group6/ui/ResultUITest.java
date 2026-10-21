package ie.ucd.bdic.group6.ui;

import ie.ucd.bdic.group6.core.snapshot.PlayerStateSnapshot;
import ie.ucd.bdic.group6.core.snapshot.PropertyRowSnapshot;
import ie.ucd.bdic.group6.core.snapshot.SessionSnapshot;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ResultUITest {

    @Test
    void victoryReasonExplainsCompletedPropertySetWin() {
        UiSnapshot snapshot = finishedSnapshot(
                "winner",
                List.of(),
                player("winner", "Winner", 25, 3),
                player("guest", "Guest", 4, 1));

        assertEquals(
                "Completed 3 full property sets.",
                ResultUI.victoryReason(snapshot, snapshot.player("winner").orElseThrow()));
    }

    @Test
    void victoryReasonExplainsForfeitWin() {
        UiSnapshot snapshot = finishedSnapshot(
                "winner",
                List.of("collector"),
                player("winner", "Winner", 4, 1),
                player("collector", "Collector", 25, 3));

        assertEquals(
                "Last active player after forfeits.",
                ResultUI.victoryReason(snapshot, snapshot.player("winner").orElseThrow()));
    }

    @Test
    void standingsKeepWinnerFirstThenSortRemainingPlayersByScore() {
        UiSnapshot snapshot = finishedSnapshot(
                "winner",
                List.of("collector"),
                player("winner", "Winner", 4, 1),
                player("collector", "Collector", 25, 3),
                player("builder", "Builder", 18, 2),
                player("banker", "Banker", 20, 2));

        assertEquals(List.of(
                new ResultUI.StandingRow(1, true, "Winner", 1, 4),
                new ResultUI.StandingRow(2, false, "Collector", 3, 25),
                new ResultUI.StandingRow(3, false, "Banker", 2, 20),
                new ResultUI.StandingRow(4, false, "Builder", 2, 18)
        ), ResultUI.standingRows(snapshot));
    }

    @Test
    void standingSummaryUsesSingularAndPluralSetLabels() {
        assertEquals(
                "1 full set  |  Bank $4M",
                new ResultUI.StandingRow(1, true, "Winner", 1, 4).summary());
        assertEquals(
                "3 full sets  |  Bank $25M",
                new ResultUI.StandingRow(2, false, "Collector", 3, 25).summary());
    }

    private UiSnapshot finishedSnapshot(
            String winnerPlayerId,
            List<String> forfeitedPlayerIds,
            PlayerStateSnapshot... players) {
        return UiSnapshot.from(new SessionSnapshot(
                "s1",
                "ABC123",
                "winner",
                players.length,
                "FINISHED",
                "",
                0,
                0,
                winnerPlayerId,
                0,
                0,
                List.of(),
                List.of(),
                List.of(),
                forfeitedPlayerIds,
                List.of(players),
                List.of()));
    }

    private PlayerStateSnapshot player(String id, String name, int bankValueM, int completedSets) {
        return new PlayerStateSnapshot(
                id,
                name,
                "CONNECTED",
                true,
                "",
                false,
                0,
                bankValueM,
                List.of(),
                propertyRows(id, completedSets));
    }

    private List<PropertyRowSnapshot> propertyRows(String playerId, int completedSets) {
        return java.util.stream.IntStream.range(0, completedSets)
                .mapToObj(index -> new PropertyRowSnapshot(
                        playerId + "-row-" + index,
                        "RED",
                        true,
                        0,
                        false,
                        false,
                        List.of()))
                .toList();
    }
}
