package ie.ucd.bdic.group6.ui;

import javafx.fxml.FXML;

public class MenuUI {

    @FXML
    private void startNewGame() {
        SceneManager.showLobby();
    }

    @FXML
    private void joinGame() {
        SceneManager.showLobby();
    }

    @FXML
    private void rejoinSession() {
        SceneManager.showGame();
    }

    @FXML
    private void openHowToPlay() {
        SceneManager.showHowToPlay();
    }

    @FXML
    private void exitApplication() {
        SceneManager.exitApplication();
    }
}
