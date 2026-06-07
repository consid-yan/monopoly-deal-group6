package ie.ucd.bdic.group6.ui;

import ie.ucd.bdic.group6.app.GameSessionCoordinator;
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

    private final GameSessionCoordinator context;

    @FXML
    private TextField roomCodeField;

    @FXML
    private TextField hostField;

    @FXML
    private TextField playerCountField;

    @FXML
    private TextField joinHostField;

    @FXML
    private ListView<String> playersList;

    @FXML
    private Button startGameButton;

    @FXML
    private TextArea statusArea;

    public LobbyUI(GameSessionCoordinator context) {
        this.context = context;
    }

    @FXML
    private void initialize() {
        roomCodeField.clear();
        hostField.clear();
        playerCountField.clear();
        joinHostField.clear();
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
                    if (result.accepted()) {
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
            roomCodeField.clear();
            joinHostField.clear();
            hostField.clear();
            playerCountField.clear();
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

        roomCodeField.setText(snapshot.roomCode());
        hostField.setText(snapshot.playerName(snapshot.hostPlayerId()));
        playerCountField.setText(snapshot.players().size() + " / " + snapshot.maxPlayers());
        joinHostField.setText(context.joinHost());
        playersList.setItems(FXCollections.observableArrayList(playerRows(snapshot)));
        if (startGameButton != null) {
            startGameButton.setDisable(!context.playerId().equals(snapshot.hostPlayerId()));
        }
        MessageUI.show(statusArea, "Waiting for the host to start");
    }

    private List<String> playerRows(UiSnapshot snapshot) {
        List<String> rows = new ArrayList<>();
        for (UiSnapshot.PlayerView player : snapshot.players()) {
            List<String> tags = new ArrayList<>();
            if (player.playerId().equals(snapshot.hostPlayerId())) {
                tags.add("Host");
            }
            if (!player.connected()) {
                tags.add("Disconnected");
            }
            if (player.playerId().equals(context.playerId())) {
                tags.add("You");
            }
            if (tags.isEmpty()) {
                rows.add(player.displayName());
            } else {
                rows.add(player.displayName() + " (" + String.join(", ", tags) + ")");
            }
        }
        return rows;
    }

    private void showStatus(String message) {
        MessageUI.show(statusArea, message);
    }
}
