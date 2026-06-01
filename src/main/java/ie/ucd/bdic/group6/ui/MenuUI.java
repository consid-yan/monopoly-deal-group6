package ie.ucd.bdic.group6.ui;

import javafx.fxml.FXML;
import javafx.application.Platform;

import java.util.concurrent.CompletionException;

public class MenuUI {
    private final UiSessionContext context;

    public MenuUI(UiSessionContext context) {
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
                context.joinSession(request.host(), request.port(), request.sessionCode(), request.playerName())
                        .whenComplete(this::openNextScreen));
    }

    @FXML
    private void rejoinSession() {
        UiDialogs.promptRejoinGame().ifPresent(request ->
                context.rejoinSession(
                                request.host(),
                                request.port(),
                                request.sessionIdentity(),
                                request.playerId(),
                                request.playerToken())
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

    private void openNextScreen(ie.ucd.bdic.group6.network.protocol.ServerResult result, Throwable error) {
        Platform.runLater(() -> {
            if (error != null) {
                Throwable cause = error instanceof CompletionException && error.getCause() != null
                        ? error.getCause()
                        : error;
                UiDialogs.showError("Connection Failed", cause.getMessage());
                return;
            }
            if (!result.success()) {
                UiDialogs.showError("Command Failed", result.code() + ": " + result.message());
                return;
            }
            UiSnapshot snapshot = UiSnapshot.from(result.data());
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
