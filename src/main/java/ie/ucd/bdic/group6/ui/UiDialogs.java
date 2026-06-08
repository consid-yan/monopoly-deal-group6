package ie.ucd.bdic.group6.ui;

import java.util.Optional;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import org.kordamp.ikonli.javafx.FontIcon;

final class UiDialogs {
    private static final double FORM_DIALOG_WIDTH = 390;
    private static final String DIALOG_ICON = "/images/icon.png";
    private static final String ERROR_GRAPHIC = "/images/error.png";

    static Optional<NewGameRequest> promptNewGame() {
        TextField playerNameField = field("Player name");
        TextField maxPlayersField = field("Number from 2 to 5");
        maxPlayersField.setText("3");
        Optional<ButtonType> result = showForm("New Game", "Host a new multiplayer game",
                row("Player Name", playerNameField),
                row("Maximum Players", maxPlayersField, "Choose between 2 and 5 players"));
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
        TextField roomCodeField = field("Room code");
        TextField hostField = field("Server host");
        TextField playerNameField = field("Player name");
        Optional<ButtonType> result = showForm("Join Game", "Connect to an existing room",
                row("Room Code", roomCodeField),
                row("Host IP", hostField),
                row("Player Name", playerNameField));
        if (result.isEmpty() || result.get() != ButtonType.OK) {
            return Optional.empty();
        }
        try {
            return Optional.of(new JoinRequest(
                    requireText(roomCodeField.getText(), "Room code"),
                    requireText(hostField.getText(), "Host"),
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
        makeUndecorated(alert);
        decorateDialog(alert, title);
        applyTransparentScene(alert);
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
        makeUndecorated(dialog);
        decorateDialog(dialog, title);
        applyTransparentScene(dialog);
        applyDialogIcon(dialog);
        dialog.setTitle(title);
        dialog.setHeaderText(null);
        dialog.getDialogPane().getButtonTypes().setAll(ButtonType.OK, ButtonType.CANCEL);
        dialog.getDialogPane().setMinWidth(FORM_DIALOG_WIDTH);
        dialog.getDialogPane().setPrefWidth(FORM_DIALOG_WIDTH);
        dialog.getDialogPane().setMaxWidth(FORM_DIALOG_WIDTH);
        styleDialogButtons(dialog);

        var heading = new Label(header);
        heading.getStyleClass().add("app-dialog-heading");

        var form = new VBox(16);
        form.getStyleClass().add("app-dialog-form");
        for (FormRow row : rows) {
            form.getChildren().add(fieldBlock(row));
        }

        VBox content = new VBox(20, heading, form);
        dialog.getDialogPane().setContent(content);
        return dialog.showAndWait();
    }

    private static VBox fieldBlock(FormRow row) {
        Label label = new Label(row.label());
        label.getStyleClass().add("app-dialog-field-label");
        if (row.field() instanceof Region region) {
            region.setMaxWidth(Double.MAX_VALUE);
        }

        VBox block = new VBox(6, label, row.field());
        block.getStyleClass().add("app-dialog-field");
        if (row.helper() != null && !row.helper().isBlank()) {
            Label helper = new Label(row.helper());
            helper.getStyleClass().add("app-dialog-field-helper");
            block.getChildren().add(helper);
        }
        return block;
    }

    private static void styleDialogButtons(Dialog<?> dialog) {
        Node okNode = dialog.getDialogPane().lookupButton(ButtonType.OK);
        if (okNode instanceof Button okButton) {
            okButton.setText("Confirm");
            okButton.getStyleClass().add("app-dialog-confirm-button");
        }
        Node cancelNode = dialog.getDialogPane().lookupButton(ButtonType.CANCEL);
        if (cancelNode instanceof Button cancelButton) {
            cancelButton.setText("Cancel");
            cancelButton.getStyleClass().add("app-dialog-cancel-button");
        }
    }

    private static void makeUndecorated(Dialog<?> dialog) {
        dialog.initStyle(StageStyle.TRANSPARENT);
    }

    private static void decorateDialog(Dialog<?> dialog, String title) {
        dialog.getDialogPane().getStylesheets().add(UiTheme.appStylesheet());
        dialog.getDialogPane().getStyleClass().add("app-dialog");
        dialog.getDialogPane().setHeader(customTitleBar(dialog, title));
    }

    private static HBox customTitleBar(Dialog<?> dialog, String title) {
        HBox titleBar = new HBox(10);
        titleBar.setAlignment(Pos.CENTER_LEFT);
        titleBar.getStyleClass().add("app-dialog-titlebar");

        Label badge = new Label("M");
        badge.getStyleClass().add("app-dialog-badge");

        Label titleLabel = new Label(title == null || title.isBlank() ? "Monopoly Deal" : title);
        titleLabel.getStyleClass().add("app-dialog-title");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button closeButton = new Button();
        FontIcon closeIcon = new FontIcon("bx-x");
        closeIcon.getStyleClass().add("app-dialog-close-icon");
        closeButton.setGraphic(closeIcon);
        closeButton.getStyleClass().add("app-dialog-close-button");
        closeButton.setOnAction(_ -> dialog.close());

        titleBar.getChildren().addAll(badge, titleLabel, spacer, closeButton);
        enableTitleBarDrag(dialog, titleBar);
        return titleBar;
    }

    private static void enableTitleBarDrag(Dialog<?> dialog, Node titleBar) {
        double[] dragOffset = new double[2];
        titleBar.setOnMousePressed(event -> {
            var window = dialog.getDialogPane().getScene().getWindow();
            dragOffset[0] = window.getX() - event.getScreenX();
            dragOffset[1] = window.getY() - event.getScreenY();
        });
        titleBar.setOnMouseDragged(event -> {
            var window = dialog.getDialogPane().getScene().getWindow();
            window.setX(event.getScreenX() + dragOffset[0]);
            window.setY(event.getScreenY() + dragOffset[1]);
        });
    }

    private static void applyTransparentScene(Dialog<?> dialog) {
        dialog.setOnShowing(_ -> {
            var scene = dialog.getDialogPane().getScene();
            if (scene != null) {
                scene.setFill(Color.TRANSPARENT);
            }
        });
    }

    private static void applyDialogIcon(Dialog<?> dialog) {
        Image icon = loadImage(DIALOG_ICON);
        if (icon == null) {
            return;
        }
        dialog.setOnShown(_ -> {
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
        field.getStyleClass().add("app-dialog-input");
        return field;
    }

    private static FormRow row(String label, Node field) {
        return row(label, field, null);
    }

    private static FormRow row(String label, Node field, String helper) {
        return new FormRow(label, field, helper);
    }

    private static int parseMaxPlayers(String raw) {
        try {
            int maxPlayers = Integer.parseInt(requireText(raw, "Maximum Players"));
            if (maxPlayers < 2 || maxPlayers > 5) {
                throw new IllegalArgumentException("Maximum Players must be between 2 and 5.");
            }
            return maxPlayers;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Maximum Players must be an integer.", e);
        }
    }

    private static String requireText(String raw, String label) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException(label + " is required");
        }
        return raw.trim();
    }

    record NewGameRequest(String playerName, int maxPlayers) {}

    record JoinRequest(String roomCode, String host, String playerName) {}

    record RejoinRequest(String roomCode) {}

    private record FormRow(String label, Node field, String helper) {}
}
