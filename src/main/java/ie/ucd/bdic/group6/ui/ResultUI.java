package ie.ucd.bdic.group6.ui;

import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.ListView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;

import java.util.Comparator;
import java.util.List;

public class ResultUI {
    private final UiSessionContext context;

    @FXML
    private TextField winnerField;

    @FXML
    private TextField completedSetsField;

    @FXML
    private ListView<String> playerSummaryList;

    @FXML
    private ListView<String> forfeitList;

    @FXML
    private TextArea finalResultArea;

    public ResultUI(UiSessionContext context) {
        this.context = context;
    }

    @FXML
    private void initialize() {
        winnerField.clear();
        completedSetsField.clear();
        playerSummaryList.setItems(FXCollections.observableArrayList());
        forfeitList.setItems(FXCollections.observableArrayList());
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
        forfeitList.setItems(FXCollections.observableArrayList(forfeitRows(snapshot)));
        MessageUI.show(finalResultArea, winnerField.getText()
                + " won the session. Gameplay has terminated and further game actions are rejected.");
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

    private List<String> forfeitRows(UiSnapshot snapshot) {
        if (snapshot.forfeitedPlayerIds().isEmpty()) {
            return List.of("No disconnect forfeits");
        }
        return snapshot.forfeitedPlayerIds().stream()
                .map(playerId -> snapshot.playerName(playerId) + " - Forfeit")
                .toList();
    }
}
