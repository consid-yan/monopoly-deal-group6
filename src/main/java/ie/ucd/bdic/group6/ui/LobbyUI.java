package ie.ucd.bdic.group6.ui;

import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ListView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;

public class LobbyUI {

    @FXML
    private TextField sessionCodeField;

    @FXML
    private TextField hostField;

    @FXML
    private TextField playerCountField;

    @FXML
    private ListView<String> playersList;

    @FXML
    private Button readyButton;

    @FXML
    private TextArea statusArea;

    private boolean ready;

    @FXML
    private void initialize() {
        sessionCodeField.setText("MD-6428");
        hostField.setText("Alex");
        playerCountField.setText("4 / 5");
        playersList.setItems(FXCollections.observableArrayList(
                "Alex - Host - Ready",
                "Morgan - Ready",
                "Riley - Choosing cards",
                "You - Not ready"
        ));
        statusArea.setText("Session created. Waiting for all players to mark ready.");
    }

    @FXML
    private void toggleReady() {
        ready = !ready;
        readyButton.setText(ready ? "Cancel Ready" : "Ready Up");
        playersList.getItems().set(3, ready ? "You - Ready" : "You - Not ready");
        statusArea.setText(ready
                ? "You are ready. The host can start the game."
                : "You are no longer ready.");
    }

    @FXML
    private void startGame() {
        SceneManager.showGame();
    }

    @FXML
    private void openHowToPlay() {
        SceneManager.showHowToPlay();
    }

    @FXML
    private void leaveLobby() {
        SceneManager.showMenu();
    }
}
