package ie.ucd.bdic.group6.ui;

import ie.ucd.bdic.group6.app.GameSessionCoordinator;
import ie.ucd.bdic.group6.network.message.ServerResponse;
import javafx.fxml.FXML;
import javafx.application.Platform;

import java.util.concurrent.CompletionException;

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
                Throwable cause = error instanceof CompletionException && error.getCause() != null
                        ? error.getCause()
                        : error;
                UiDialogs.showError("Connection Failed", cause.getMessage());
                return;
            }
            if (!result.accepted()) {
                UiDialogs.showError("Command Failed", result.message());
                return;
            }
            UiSnapshot snapshot = UiSnapshot.from(result.payload());
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
