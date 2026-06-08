package ie.ucd.bdic.group6.ui;

import ie.ucd.bdic.group6.app.GameSessionCoordinator;
import ie.ucd.bdic.group6.network.message.ServerResponse;
import javafx.fxml.FXML;
import javafx.application.Platform;

public class MenuUI {

    private final GameSessionCoordinator context;

    public MenuUI(GameSessionCoordinator context) {
        this.context = context;
    }

    @FXML
    private void startNewGame() {
        UiDialogs.promptNewGame().ifPresent(request ->
                context.createHostedSession(request.playerName(), request.maxPlayers())
                        .whenComplete(this::openNextScreen));
    }

    @FXML
    private void joinGame() {
        UiDialogs.promptJoinGame().ifPresent(request ->
                context.joinSession(request.host(), request.roomCode(), request.playerName())
                        .whenComplete(this::openNextScreen));
    }

    @FXML
    private void rejoinSession() {
        UiDialogs.promptRejoinGame().ifPresent(request ->
                context.rejoinSession(request.roomCode())
                        .whenComplete(this::openNextScreen));
    }

    @FXML
    private void openHowToPlay() {
        SceneManager.showHowToPlay();
    }

    @FXML
    private void exitApplication() {
        SceneManager.exitApplication();
    }

    private void openNextScreen(ServerResponse result, Throwable error) {
        Platform.runLater(() -> {
            if (error != null) {
                UiDialogs.showError("Connection Failed", "Unable to connect to the game server. " +
                        "\nCheck the host address and make sure the host has started a game.");
                return;
            }
            if (!result.accepted()) {
                UiDialogs.showError("Command Failed", result.message());
                return;
            }
            UiSnapshot snapshot = result.snapshot() == null
                    ? UiSnapshot.empty()
                    : UiSnapshot.from(result.snapshot());
            if ("ACTIVE".equals(snapshot.status())) {
                SceneManager.showGame();
            } else if ("FINISHED".equals(snapshot.status())) {
                SceneManager.showResult();
            } else {
                SceneManager.showLobby();
            }
        });
    }
}
