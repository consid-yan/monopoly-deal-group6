package ie.ucd.bdic.group6.ui;

import java.util.Optional;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;

final class UiDialogs {
    private UiDialogs() {
    }

    static Optional<NewGameRequest> promptNewGame() {
        TextField playerNameField = field("Player name");
        TextField maxPlayersField = field("2-5");
        Optional<ButtonType> result = showForm("New Game", "Create a hosted multiplayer session",
                row("Player Name", playerNameField),
                row("Max Players", maxPlayersField));
        if (result.isEmpty() || result.get() != ButtonType.OK) {
            return Optional.empty();
        }
        try {
            String playerName = requireText(playerNameField.getText(), "Player name");
            int maxPlayers = Integer.parseInt(requireText(maxPlayersField.getText(), "Max players"));
            if (maxPlayers < 2 || maxPlayers > 5) {
                throw new IllegalArgumentException("Max players must be between 2 and 5");
            }
            return Optional.of(new NewGameRequest(playerName, maxPlayers));
        } catch (IllegalArgumentException e) {
            showError("Invalid New Game Settings", e.getMessage());
            return Optional.empty();
        }
    }

    static Optional<JoinRequest> promptJoinGame() {
        TextField hostField = field("Server host");
        TextField portField = field("Server port");
        TextField sessionCodeField = field("Session code");
        TextField playerNameField = field("Player name");
        Optional<ButtonType> result = showForm("Join Game", "Connect to an existing session",
                row("Host", hostField),
                row("Port", portField),
                row("Session Code", sessionCodeField),
                row("Player Name", playerNameField));
        if (result.isEmpty() || result.get() != ButtonType.OK) {
            return Optional.empty();
        }
        try {
            return Optional.of(new JoinRequest(
                    requireText(hostField.getText(), "Host"),
                    parsePort(portField.getText()),
                    requireText(sessionCodeField.getText(), "Session code"),
                    requireText(playerNameField.getText(), "Player name")));
        } catch (IllegalArgumentException e) {
            showError("Invalid Join Settings", e.getMessage());
            return Optional.empty();
        }
    }

    static Optional<RejoinRequest> promptRejoinGame() {
        TextField hostField = field("Server host");
        TextField portField = field("Server port");
        TextField sessionIdentityField = field("Session id or code");
        TextField playerIdField = field("Prior player id");
        Optional<ButtonType> result = showForm("Rejoin Session", "Reconnect with the same player identity",
                row("Host", hostField),
                row("Port", portField),
                row("Session", sessionIdentityField),
                row("Player ID", playerIdField));
        if (result.isEmpty() || result.get() != ButtonType.OK) {
            return Optional.empty();
        }
        try {
            return Optional.of(new RejoinRequest(
                    requireText(hostField.getText(), "Host"),
                    parsePort(portField.getText()),
                    requireText(sessionIdentityField.getText(), "Session"),
                    requireText(playerIdField.getText(), "Player ID")));
        } catch (IllegalArgumentException e) {
            showError("Invalid Rejoin Settings", e.getMessage());
            return Optional.empty();
        }
    }

    static void showError(String title, String message) {
        var alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message == null || message.isBlank() ? "The operation failed." : message);
        alert.showAndWait();
    }

    private static Optional<ButtonType> showForm(String title, String header, FormRow... rows) {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle(title);
        dialog.setHeaderText(header);
        dialog.getDialogPane().getButtonTypes().setAll(ButtonType.OK, ButtonType.CANCEL);
        var grid = new GridPane();
        grid.setHgap(12);
        grid.setVgap(10);
        grid.setPadding(new Insets(10));
        for (int index = 0; index < rows.length; index++) {
            grid.add(new Label(rows[index].label()), 0, index);
            grid.add(rows[index].field(), 1, index);
        }
        dialog.getDialogPane().setContent(grid);
        return dialog.showAndWait();
    }

    private static TextField field(String prompt) {
        TextField field = new TextField();
        field.setPromptText(prompt);
        return field;
    }

    private static FormRow row(String label, Node field) {
        return new FormRow(label, field);
    }

    private static int parsePort(String raw) {
        try {
            int port = Integer.parseInt(requireText(raw, "Port"));
            if (port < 0 || port > 65535) {
                throw new IllegalArgumentException("Port must be between 0 and 65535");
            }
            return port;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Port must be a number", e);
        }
    }

    private static String requireText(String raw, String label) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException(label + " is required");
        }
        return raw.trim();
    }

    record NewGameRequest(String playerName, int maxPlayers) {}

    record JoinRequest(String host, int port, String sessionCode, String playerName) {}

    record RejoinRequest(String host, int port, String sessionIdentity, String playerId) {}

    private record FormRow(String label, Node field) {}
}
