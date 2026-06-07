package ie.ucd.bdic.group6.ui;

import java.util.Optional;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.GridPane;
import javafx.stage.Stage;

final class UiDialogs {
    private static final String DIALOG_ICON = "/images/icon.png";
    private static final String ERROR_GRAPHIC = "/images/error.png";

    static Optional<NewGameRequest> promptNewGame() {
        TextField playerNameField = field("Player name");
        TextField maxPlayersField = field("Number from 2 to 5");
        maxPlayersField.setText("3");
        Optional<ButtonType> result = showForm("New Game", "Create a hosted multiplayer session",
                row("Player Name", playerNameField),
                row("Max Players (2-5)", maxPlayersField));
        if (result.isEmpty() || result.get() != ButtonType.OK) {
            return Optional.empty();
        }
        try {
            String playerName = requireText(playerNameField.getText(), "Player name");
            return Optional.of(new NewGameRequest(playerName, parseMaxPlayers(maxPlayersField.getText())));
        } catch (IllegalArgumentException e) {
            showError("Invalid New Game Settings", e.getMessage());
            return Optional.empty();
        }
    }

    static Optional<JoinRequest> promptJoinGame() {
        TextField hostField = field("Server host");
        TextField roomCodeField = field("Room code");
        TextField playerNameField = field("Player name");
        Optional<ButtonType> result = showForm("Join Game", "Connect to an existing session",
                row("Host", hostField),
                row("Room Code", roomCodeField),
                row("Player Name", playerNameField));
        if (result.isEmpty() || result.get() != ButtonType.OK) {
            return Optional.empty();
        }
        try {
            return Optional.of(new JoinRequest(
                    requireText(hostField.getText(), "Host"),
                    requireText(roomCodeField.getText(), "Room code"),
                    requireText(playerNameField.getText(), "Player name")));
        } catch (IllegalArgumentException e) {
            showError("Invalid Join Settings", e.getMessage());
            return Optional.empty();
        }
    }

    static Optional<RejoinRequest> promptRejoinGame() {
        TextField roomCodeField = field("Room code");
        Optional<ButtonType> result = showForm("Rejoin Session", "Reconnect with the same player id",
                row("Room Code", roomCodeField));
        if (result.isEmpty() || result.get() != ButtonType.OK) {
            return Optional.empty();
        }
        try {
            return Optional.of(new RejoinRequest(
                    requireText(roomCodeField.getText(), "Room code")));
        } catch (IllegalArgumentException e) {
            showError("Invalid Rejoin Settings", e.getMessage());
            return Optional.empty();
        }
    }

    static void showError(String title, String message) {
        var alert = new Alert(Alert.AlertType.ERROR);
        applyDialogIcon(alert);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message == null || message.isBlank() ? "The operation failed." : message);
        ImageView errorGraphic = errorGraphic();
        if (errorGraphic != null) {
            alert.setGraphic(errorGraphic);
        }
        alert.showAndWait();
    }

    private static ImageView errorGraphic() {
        Image image = loadImage(ERROR_GRAPHIC);
        if (image == null) {
            return null;
        }
        var imageView = new ImageView(image);
        imageView.setFitWidth(64);
        imageView.setFitHeight(64);
        imageView.setPreserveRatio(true);
        imageView.setSmooth(true);
        return imageView;
    }

    private static Optional<ButtonType> showForm(String title, String header, FormRow... rows) {
        Dialog<ButtonType> dialog = new Dialog<>();
        applyDialogIcon(dialog);
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

    private static void applyDialogIcon(Dialog<?> dialog) {
        Image icon = loadImage(DIALOG_ICON);
        if (icon == null) {
            return;
        }
        dialog.setOnShowing(_ -> {
            var scene = dialog.getDialogPane().getScene();
            if (scene != null && scene.getWindow() instanceof Stage stage) {
                stage.getIcons().setAll(icon);
            }
        });
    }

    private static Image loadImage(String resourcePath) {
        var resource = UiDialogs.class.getResource(resourcePath);
        return resource == null ? null : new Image(resource.toExternalForm());
    }

    private static TextField field(String prompt) {
        TextField field = new TextField();
        field.setPromptText(prompt);
        return field;
    }

    private static FormRow row(String label, Node field) {
        return new FormRow(label, field);
    }

    private static int parseMaxPlayers(String raw) {
        try {
            int maxPlayers = Integer.parseInt(requireText(raw, "Max players"));
            if (maxPlayers < 2 || maxPlayers > 5) {
                throw new IllegalArgumentException("Max players must be between 2 and 5.");
            }
            return maxPlayers;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Max players must be a whole number from 2 to 5.", e);
        }
    }

    private static String requireText(String raw, String label) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException(label + " is required");
        }
        return raw.trim();
    }

    record NewGameRequest(String playerName, int maxPlayers) {}

    record JoinRequest(String host, String roomCode, String playerName) {}

    record RejoinRequest(String roomCode) {}

    private record FormRow(String label, Node field) {}
}
