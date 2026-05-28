package ie.ucd.bdic.group6.ui;

import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.ListView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;

public class ResultUI {

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

    @FXML
    private void initialize() {
        winnerField.setText("Jordan");
        completedSetsField.setText("3 full sets");
        playerSummaryList.setItems(FXCollections.observableArrayList(
                "Jordan - 3 full sets - Bank $8M",
                "Riley - 2 full sets - Bank $7M",
                "Alex - 1 full set - Bank $6M",
                "Morgan - 0 full sets - Bank $4M"
        ));
        forfeitList.setItems(FXCollections.observableArrayList(
                "No defeated players",
                "No disconnect forfeits"
        ));
        finalResultArea.setText(
                "Jordan completed Blue, Red, and Green property sets. "
                        + "The session is now closed and gameplay actions are disabled."
        );
    }

    @FXML
    private void playAgain() {
        SceneManager.showLobby();
    }

    @FXML
    private void backToMenu() {
        SceneManager.showMenu();
    }

    @FXML
    private void exitApplication() {
        SceneManager.exitApplication();
    }
}
