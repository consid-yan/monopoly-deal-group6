package ie.ucd.bdic.group6.ui;

import javafx.fxml.FXML;

public class HowToPlayUI {

    @FXML
    private void backToMenu() {
        SceneManager.showMenu();
    }

    @FXML
    private void openLobby() {
        SceneManager.showLobby();
    }
}
