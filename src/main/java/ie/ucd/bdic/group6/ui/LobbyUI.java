package ie.ucd.bdic.group6.ui;

import ie.ucd.bdic.group6.command.CommandType;

import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ListView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class LobbyUI {
    private final UiSessionContext context;

    @FXML
    private TextField sessionCodeField;

    @FXML
    private TextField hostField;

    @FXML
    private TextField playerCountField;

    @FXML
    private TextField joinHostField;

    @FXML
    private TextField joinPortField;

    @FXML
    private ListView<String> playersList;

    @FXML
    private Button startGameButton;

    @FXML
    private TextArea statusArea;

    public LobbyUI(UiSessionContext context) {
        this.context = context;
    }

    @FXML
    private void initialize() {
        sessionCodeField.clear();
        hostField.clear();
        playerCountField.clear();
        joinHostField.clear();
        joinPortField.clear();
        playersList.setItems(FXCollections.observableArrayList());
        context.setSnapshotListener(this::render);
        context.setMessageListener(this::showStatus);
        render(context.latestSnapshot());
    }

    @FXML
    private void startGame() {
        UiSnapshot snapshot = context.latestSnapshot();
        if (snapshot.sessionId().isBlank()) {
            showStatus("No active session is available.");
            return;
        }
        Map<String, Object> payload = Map.of(
                "sessionId", snapshot.sessionId(),
                "playerId", context.playerId()
        );
        context.sendCommand(CommandType.START_GAME, payload)
                .thenAccept(result -> {
                    if (result.success()) {
                        javafx.application.Platform.runLater(SceneManager::showGame);
                    }
                });
    }

    @FXML
    private void openHowToPlay() {
        SceneManager.showHowToPlay();
    }

    @FXML
    private void leaveLobby() {
        context.leaveSession();
        SceneManager.showMenu();
    }

    private void render(UiSnapshot snapshot) {
        if (snapshot == null || snapshot.isEmpty()) {
            sessionCodeField.clear();
            hostField.clear();
            playerCountField.clear();
            joinHostField.clear();
            joinPortField.clear();
            playersList.setItems(FXCollections.observableArrayList());
            showStatus("Connect to a session from the main menu.");
            return;
        }
        if ("ACTIVE".equals(snapshot.status())) {
            SceneManager.showGame();
            return;
        }
        if ("FINISHED".equals(snapshot.status())) {
            SceneManager.showResult();
            return;
        }

        sessionCodeField.setText(snapshot.sessionCode());
        hostField.setText(snapshot.playerName(snapshot.hostPlayerId()));
        playerCountField.setText(snapshot.players().size() + " / " + snapshot.maxPlayers());
        joinHostField.setText(context.joinHost());
        joinPortField.setText(context.joinPort());
        playersList.setItems(FXCollections.observableArrayList(playerRows(snapshot)));
        if (startGameButton != null) {
            startGameButton.setDisable(!context.playerId().equals(snapshot.hostPlayerId()));
        }
        MessageUI.show(statusArea, """
                Session ID: %s
                Your Player ID: %s
                Your Player Token: %s
                Join Host: %s
                Join Port: %s
                Waiting for the host to start when 2 to 5 players are present.
                """.formatted(
                snapshot.sessionId(),
                context.playerId(),
                context.playerToken(),
                context.joinHost(),
                context.joinPort()));
    }

    private List<String> playerRows(UiSnapshot snapshot) {
        List<String> rows = new ArrayList<>();
        for (UiSnapshot.PlayerView player : snapshot.players()) {
            List<String> tags = new ArrayList<>();
            if (player.playerId().equals(snapshot.hostPlayerId())) {
                tags.add("Host");
            }
            tags.add(player.playerStatus());
            if (!player.connected()) {
                tags.add("Disconnected");
            }
            if (player.playerId().equals(context.playerId())) {
                tags.add("You");
            }
            rows.add(player.displayName() + " - " + String.join(" - ", tags));
        }
        return rows;
    }

    private void showStatus(String message) {
        MessageUI.show(statusArea, message);
    }
}
