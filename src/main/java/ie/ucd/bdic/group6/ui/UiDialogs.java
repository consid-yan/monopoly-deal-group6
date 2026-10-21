package ie.ucd.bdic.group6.ui;

import java.util.Optional;
import java.util.function.Supplier;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.DialogEvent;
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
    private static final ButtonType CONFIRM_BUTTON = new ButtonType("Confirm", ButtonBar.ButtonData.OK_DONE);
    private static final ButtonType CANCEL_BUTTON = new ButtonType("Cancel", ButtonBar.ButtonData.CANCEL_CLOSE);
    private static final ButtonType ERROR_OK_BUTTON = new ButtonType("OK", ButtonBar.ButtonData.OK_DONE);

    private static final double FORM_DIALOG_WIDTH = 390;
    private static final double DEFAULT_DIALOG_WIDTH = 430;
    private static final String DIALOG_ICON = "/images/icon.png";
    private static final String ERROR_GRAPHIC = "/images/error.png";

    static Optional<NewGameRequest> promptNewGame() {
        TextField playerNameField = field("Player name");
        TextField maxPlayersField = field("Number from 2 to 5");
        maxPlayersField.setText("3");
        return promptForm("New Game", "Host a new multiplayer game", "Invalid New Game Settings",
                () -> new NewGameRequest(
                        requireText(playerNameField.getText(), "Player name"),
                        parseMaxPlayers(maxPlayersField.getText())),
                row("Player Name", playerNameField),
                row("Maximum Players", maxPlayersField, "Choose between 2 and 5 players"));
    }

    static Optional<JoinRequest> promptJoinGame() {
        TextField roomCodeField = field("Room code");
        TextField hostField = field("Server host");
        TextField playerNameField = field("Player name");
        return promptForm("Join Game", "Connect to an existing room", "Invalid Join Settings",
                () -> new JoinRequest(
                        requireText(roomCodeField.getText(), "Room code"),
                        requireText(hostField.getText(), "Host"),
                        requireText(playerNameField.getText(), "Player name")),
                row("Room Code", roomCodeField),
                row("Host IP", hostField),
                row("Player Name", playerNameField));
    }

    static Optional<RejoinRequest> promptRejoinGame() {
        TextField roomCodeField = field("Room code");
        return promptForm("Rejoin Session", "Reconnect with the same player id", "Invalid Rejoin Settings",
                () -> new RejoinRequest(requireText(roomCodeField.getText(), "Room code")),
                row("Room Code", roomCodeField));
    }

    static void showError(String title, String message) {
        Dialog<ButtonType> dialog = messageDialog(title, message);
        dialog.showAndWait();
    }

    static void showWarning(String title, String message) {
        Dialog<ButtonType> dialog = messageDialog(title, message);
        dialog.show();
    }

    static <R> Dialog<R> createDialog(String title) {
        Dialog<R> dialog = new Dialog<>();
        prepareDialog(dialog, title);
        setDialogWidth(dialog, DEFAULT_DIALOG_WIDTH);
        return dialog;
    }

    static ButtonType confirmButton(String text) {
        return new ButtonType(text, ButtonBar.ButtonData.OK_DONE);
    }

    static ButtonType cancelButton(String text) {
        return new ButtonType(text, ButtonBar.ButtonData.CANCEL_CLOSE);
    }

    static ButtonType secondaryButton(String text) {
        return new ButtonType(text, ButtonBar.ButtonData.NO);
    }

    static void setDialogWidth(Dialog<?> dialog, double width) {
        dialog.getDialogPane().setMinWidth(width);
        dialog.getDialogPane().setPrefWidth(width);
        dialog.getDialogPane().setMaxWidth(width);
    }

    static void setButtons(Dialog<?> dialog, ButtonType... buttonTypes) {
        dialog.getDialogPane().getButtonTypes().setAll(buttonTypes);
        for (ButtonType buttonType : buttonTypes) {
            Node buttonNode = dialog.getDialogPane().lookupButton(buttonType);
            if (buttonNode instanceof Button button) {
                button.getStyleClass().removeAll("app-dialog-confirm-button", "app-dialog-cancel-button");
                button.getStyleClass().add(isPrimaryButton(buttonType)
                        ? "app-dialog-confirm-button"
                        : "app-dialog-cancel-button");
            }
        }
    }

    static VBox messageContent(String heading, String message) {
        VBox content = new VBox(14);
        if (heading != null && !heading.isBlank()) {
            content.getChildren().add(headingLabel(heading));
        }
        content.getChildren().add(bodyLabel(message));
        return content;
    }

    static Label headingLabel(String text) {
        Label label = new Label(text == null ? "" : text);
        label.setWrapText(true);
        label.getStyleClass().add("app-dialog-heading");
        return label;
    }

    static Label bodyLabel(String text) {
        Label label = new Label(text == null || text.isBlank() ? "No details available." : text);
        label.setWrapText(true);
        label.getStyleClass().add("app-dialog-body");
        return label;
    }

    private static Dialog<ButtonType> messageDialog(String title, String message) {
        Dialog<ButtonType> dialog = createDialog(title);
        setDialogWidth(dialog, 520);
        setButtons(dialog, ERROR_OK_BUTTON);

        Label body = bodyLabel(message == null || message.isBlank() ? "The operation failed." : message);
        HBox content = new HBox(18, errorGraphic(), body);
        content.setAlignment(Pos.CENTER_LEFT);
        dialog.getDialogPane().setContent(content);
        return dialog;
    }

    private static Node errorGraphic() {
        Image image = loadImage(ERROR_GRAPHIC);
        if (image == null) {
            Label fallback = new Label("!");
            fallback.getStyleClass().add("app-dialog-image-fallback");
            return fallback;
        }
        ImageView imageView = new ImageView(image);
        imageView.setFitWidth(64);
        imageView.setFitHeight(64);
        imageView.setPreserveRatio(true);
        imageView.setSmooth(true);
        return imageView;
    }

    private static boolean isPrimaryButton(ButtonType buttonType) {
        ButtonBar.ButtonData data = buttonType.getButtonData();
        return data == ButtonBar.ButtonData.OK_DONE
                || data == ButtonBar.ButtonData.YES
                || data == ButtonBar.ButtonData.APPLY
                || data == ButtonBar.ButtonData.FINISH
                || data == ButtonBar.ButtonData.NEXT_FORWARD;
    }

    private static Optional<ButtonType> showForm(String title, String header, FormRow... rows) {
        Dialog<ButtonType> dialog = createDialog(title);
        dialog.setTitle(title);
        dialog.setHeaderText(null);
        setDialogWidth(dialog, FORM_DIALOG_WIDTH);
        setButtons(dialog, CONFIRM_BUTTON, CANCEL_BUTTON);

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

    private static <T> Optional<T> promptForm(
            String title, String header, String errorTitle, Supplier<T> resultFactory, FormRow... rows) {
        Optional<ButtonType> result = showForm(title, header, rows);
        if (result.isEmpty() || result.get() != CONFIRM_BUTTON) {
            return Optional.empty();
        }
        try {
            return Optional.of(resultFactory.get());
        } catch (IllegalArgumentException exception) {
            showError(errorTitle, exception.getMessage());
            return Optional.empty();
        }
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

    private static void prepareDialog(Dialog<?> dialog, String title) {
        dialog.initStyle(StageStyle.TRANSPARENT);
        decorateDialog(dialog, title);
        applyTransparentScene(dialog);
        applyDialogIcon(dialog);
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
        dialog.addEventHandler(DialogEvent.DIALOG_SHOWING, _ -> {
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
        dialog.addEventHandler(DialogEvent.DIALOG_SHOWN, _ -> {
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
