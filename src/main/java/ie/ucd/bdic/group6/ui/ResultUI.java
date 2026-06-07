package ie.ucd.bdic.group6.ui;

import ie.ucd.bdic.group6.app.GameSessionCoordinator;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.ListView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class ResultUI {

    private final GameSessionCoordinator context;

    @FXML
    private TextField winnerField;

    @FXML
    private TextField completedSetsField;

    @FXML
    private ListView<String> playerSummaryList;

    @FXML
    private TextArea finalResultArea;

    public ResultUI(GameSessionCoordinator context) {
        this.context = context;
    }

    @FXML
    private void initialize() {
        winnerField.clear();
        completedSetsField.clear();
        playerSummaryList.setItems(FXCollections.observableArrayList());
        MessageUI.clear(finalResultArea);
        context.setSnapshotListener(this::render);
        context.setMessageListener(message -> MessageUI.show(finalResultArea, message));
        render(context.latestSnapshot());
    }

    @FXML
    private void playAgain() {
        context.leaveSession();
        SceneManager.showMenu();
    }

    @FXML
    private void backToMenu() {
        context.leaveSession();
        SceneManager.showMenu();
    }

    @FXML
    private void exitApplication() {
        SceneManager.exitApplication();
    }

    private void render(UiSnapshot snapshot) {
        if (snapshot == null || snapshot.isEmpty()) {
            MessageUI.show(finalResultArea, "No completed game result is available.");
            return;
        }
        if (!"FINISHED".equals(snapshot.status())) {
            MessageUI.show(finalResultArea, "The game is not finished yet.");
            return;
        }

        UiSnapshot.PlayerView winner = snapshot.player(snapshot.winnerPlayerId()).orElse(null);
        winnerField.setText(winner == null ? snapshot.winnerPlayerId() : winner.displayName());
        completedSetsField.setText((winner == null ? 0 : winner.completedSetCount()) + " full sets");
        playerSummaryList.setItems(FXCollections.observableArrayList(playerSummaries(snapshot)));
        MessageUI.showLines(finalResultArea, matchSummaryLines(snapshot, winner));
    }

    private List<String> matchSummaryLines(UiSnapshot snapshot, UiSnapshot.PlayerView winner) {
        int completedSets = winner == null ? 0 : winner.completedSetCount();
        int bankTotalValueM = winner == null ? 0 : winner.bankTotalValueM();
        String winnerName = winner == null ? snapshot.winnerPlayerId() : winner.displayName();

        List<String> lines = new ArrayList<>();
        lines.add(winnerName + " wins by completing " + completedSets + " full property sets.");
        lines.add("Final score: " + completedSets + " full sets, Bank $" + bankTotalValueM + "M.");
        if (snapshot.forfeitedPlayerIds().isEmpty()) {
            lines.add("No players forfeited.");
        } else {
            lines.add("Forfeits: " + String.join(", ", snapshot.forfeitedPlayerIds().stream()
                    .map(snapshot::playerName)
                    .toList()));
        }
        return lines;
    }

    private List<String> playerSummaries(UiSnapshot snapshot) {
        return snapshot.players().stream()
                .sorted(Comparator.comparingInt(UiSnapshot.PlayerView::completedSetCount).reversed()
                        .thenComparing(UiSnapshot.PlayerView::displayName))
                .map(player -> player.displayName()
                        + " - " + player.completedSetCount() + " full sets"
                        + " - Bank $" + player.bankTotalValueM() + "M"
                        + " - " + player.playerStatus())
                .toList();
    }
}
