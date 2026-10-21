package ie.ucd.bdic.group6.ui;

import ie.ucd.bdic.group6.app.GameSessionCoordinator;
import ie.ucd.bdic.group6.command.CommandType;

import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.TextArea;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class LobbyUI {

    private final GameSessionCoordinator context;

    @FXML
    private Label roomCodeLabel;

    @FXML
    private Label hostLabel;

    @FXML
    private Label playerCountLabel;

    @FXML
    private Label joinHostLabel;

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
        context.sendCommand(CommandType.START_GAME, payload);
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
            setReadout(roomCodeLabel, "", "Waiting for room code");
            setReadout(joinHostLabel, "", "Waiting for host address");
            setReadout(hostLabel, "", "Waiting for host");
            setReadout(playerCountLabel, "", "0 / 5");
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

        setReadout(roomCodeLabel, snapshot.roomCode(), "Waiting for room code");
        setReadout(hostLabel, snapshot.playerName(snapshot.hostPlayerId()), "Waiting for host");
        setReadout(playerCountLabel, snapshot.players().size() + " / " + snapshot.maxPlayers(), "0 / 5");
        setReadout(joinHostLabel, context.joinHost(), "Waiting for host address");
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

    private void setReadout(Label label, String value, String placeholder) {
        if (label != null) {
            label.setText(value == null || value.isBlank() ? placeholder : value);
        }
    }
}
