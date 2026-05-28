package ie.ucd.bdic.group6.ui;

import java.io.IOException;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

public final class SceneManager {

    private static final double DEFAULT_WIDTH = 1120;
    private static final double DEFAULT_HEIGHT = 720;

    private static Stage stage;

    private SceneManager() {
    }

    public static void initialize(Stage primaryStage) {
        stage = primaryStage;
        UiTheme.applyDefaultTheme();
        stage.setTitle("Monopoly Deal");
        stage.setMinWidth(1024);
        stage.setMinHeight(680);
        showMenu();
    }

    public static void showMenu() {
        show("menu.fxml");
    }

    public static void showLobby() {
        show("lobby.fxml");
    }

    public static void showGame() {
        show("game.fxml");
    }

    public static void showHowToPlay() {
        show("how-to-play.fxml");
    }

    public static void showResult() {
        show("result.fxml");
    }

    public static void exitApplication() {
        if (stage == null) {
            Platform.exit();
            return;
        }
        stage.close();
    }

    private static void show(String fxmlName) {
        if (stage == null) {
            throw new IllegalStateException("SceneManager has not been initialized.");
        }

        try {
            Parent root = FXMLLoader.load(SceneManager.class.getResource("/fxml/" + fxmlName));
            Scene scene = stage.getScene();
            if (scene == null) {
                scene = new Scene(root, DEFAULT_WIDTH, DEFAULT_HEIGHT);
                scene.getStylesheets().setAll(UiTheme.appStylesheet());
                stage.setScene(scene);
            } else {
                scene.setRoot(root);
                if (!scene.getStylesheets().contains(UiTheme.appStylesheet())) {
                    scene.getStylesheets().setAll(UiTheme.appStylesheet());
                }
            }
            stage.show();
        } catch (IOException | RuntimeException exception) {
            throw new IllegalStateException("Unable to load UI screen: " + fxmlName, exception);
        }
    }
}
