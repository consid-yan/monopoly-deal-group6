package ie.ucd.bdic.group6.ui;

import ie.ucd.bdic.group6.app.GameSessionCoordinator;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import org.kordamp.ikonli.javafx.FontIcon;

import java.util.Comparator;
import java.util.List;

public class ResultUI {

    private final GameSessionCoordinator context;

    @FXML
    private Label winnerNameLabel;

    @FXML
    private Label victoryReasonLabel;

    @FXML
    private Label completedSetsLabel;

    @FXML
    private Label bankValueLabel;

    @FXML
    private Label finalRankLabel;

    @FXML
    private ListView<StandingRow> standingsList;

    public ResultUI(GameSessionCoordinator context) {
        this.context = context;
    }

    @FXML
    private void initialize() {
        standingsList.setCellFactory(ignored -> standingCell());
        showUnavailableResult("");
        context.setSnapshotListener(this::render);
        context.setMessageListener(message -> victoryReasonLabel.setText(message == null ? "" : message));
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
            showUnavailableResult("No completed game result is available.");
            return;
        }
        if (!"FINISHED".equals(snapshot.status())) {
            showUnavailableResult("The game is not finished yet.");
            return;
        }

        UiSnapshot.PlayerView winner = snapshot.player(snapshot.winnerPlayerId()).orElse(null);
        if (winner == null) {
            showUnavailableResult("The winner is not available.");
            return;
        }

        winnerNameLabel.setText(winner.displayName() + " Wins!");
        victoryReasonLabel.setText(victoryReason(snapshot, winner));
        completedSetsLabel.setText(Integer.toString(winner.completedSetCount()));
        bankValueLabel.setText("$" + winner.bankTotalValueM() + "M");
        finalRankLabel.setText("1st");
        standingsList.setItems(FXCollections.observableArrayList(standingRows(snapshot)));
    }

    private void showUnavailableResult(String message) {
        winnerNameLabel.setText("No Result");
        victoryReasonLabel.setText(message);
        completedSetsLabel.setText("--");
        bankValueLabel.setText("--");
        finalRankLabel.setText("--");
        standingsList.setItems(FXCollections.observableArrayList());
    }

    private ListCell<StandingRow> standingCell() {
        return new ListCell<>() {
            @Override
            protected void updateItem(StandingRow row, boolean empty) {
                super.updateItem(row, empty);
                getStyleClass().remove("result-standing-winner");
                if (empty || row == null) {
                    setGraphic(null);
                    return;
                }

                Label rank = new Label(row.winner() ? "" : Integer.toString(row.rank()));
                if (row.winner()) {
                    rank.setGraphic(new FontIcon("bxs-crown"));
                }
                rank.getStyleClass().add("result-standing-rank");
                rank.setMinWidth(26);

                Label name = new Label(row.displayName());
                name.getStyleClass().add("result-standing-name");
                VBox identity = new VBox(1, name);

                Label summary = new Label(row.summary());
                summary.getStyleClass().add("result-standing-summary");

                HBox content = new HBox(8, rank, identity, summary);
                content.setAlignment(Pos.CENTER_LEFT);
                HBox.setHgrow(identity, Priority.ALWAYS);
                if (row.winner()) {
                    getStyleClass().add("result-standing-winner");
                }
                setGraphic(content);
            }
        };
    }

    static String victoryReason(UiSnapshot snapshot, UiSnapshot.PlayerView winner) {
        if (winner.completedSetCount() >= 3) {
            return "Completed " + winner.completedSetCount() + " full property sets.";
        }
        if (!snapshot.forfeitedPlayerIds().isEmpty()) {
            return "Last active player after forfeits.";
        }
        return "Last active player.";
    }

    static List<StandingRow> standingRows(UiSnapshot snapshot) {
        Comparator<UiSnapshot.PlayerView> scoreOrder =
                Comparator.comparingInt(UiSnapshot.PlayerView::completedSetCount).reversed()
                        .thenComparing(Comparator.comparingInt(UiSnapshot.PlayerView::bankTotalValueM).reversed())
                        .thenComparing(UiSnapshot.PlayerView::displayName);

        List<UiSnapshot.PlayerView> orderedPlayers = snapshot.players().stream()
                .sorted(Comparator.comparing((UiSnapshot.PlayerView player) ->
                                !player.playerId().equals(snapshot.winnerPlayerId()))
                        .thenComparing(scoreOrder))
                .toList();

        return java.util.stream.IntStream.range(0, orderedPlayers.size())
                .mapToObj(index -> {
                    UiSnapshot.PlayerView player = orderedPlayers.get(index);
                    return new StandingRow(
                            index + 1,
                            player.playerId().equals(snapshot.winnerPlayerId()),
                            player.displayName(),
                            player.completedSetCount(),
                            player.bankTotalValueM());
                })
                .toList();
    }

    record StandingRow(int rank, boolean winner, String displayName, int completedSets, int bankValueM) {
        String summary() {
            String setLabel = completedSets == 1 ? " full set" : " full sets";
            return completedSets + setLabel + "  |  Bank $" + bankValueM + "M";
        }
    }
}
