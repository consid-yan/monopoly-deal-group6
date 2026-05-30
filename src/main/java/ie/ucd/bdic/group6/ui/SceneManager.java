package ie.ucd.bdic.group6.ui;

import ie.ucd.bdic.group6.app.AppConfig;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.util.Objects;

import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.stage.Stage;

public final class SceneManager {

    private static final double DEFAULT_WIDTH = 1120;
    private static final double DEFAULT_HEIGHT = 720;
    private static final String MENU_FXML = "menu.fxml";
    private static final String LOBBY_FXML = "lobby.fxml";
    private static final String GAME_FXML = "game.fxml";
    private static final String HOW_TO_PLAY_FXML = "how-to-play.fxml";
    private static final String RESULT_FXML = "result.fxml";
    private static final String APP_ICON = "/images/icon.png";

    private static Stage stage;
    private static UiSessionContext sessionContext;
    private static String currentScreen;
    private static String howToPlayReturnScreen;

    private SceneManager() {
    }

    public static void initialize(Stage primaryStage, AppConfig appConfig) {
        stage = primaryStage;
        sessionContext = new UiSessionContext(appConfig);
        UiTheme.applyDefaultTheme();
        stage.setTitle("Monopoly Deal");
        stage.getIcons().setAll(new Image(Objects.requireNonNull(SceneManager.class.getResource(APP_ICON))
                .toExternalForm()));
        stage.setMinWidth(1024);
        stage.setMinHeight(680);
        showMenu();
    }

    public static UiSessionContext sessionContext() {
        if (sessionContext == null) {
            throw new IllegalStateException("SceneManager has not been initialized.");
        }
        return sessionContext;
    }

    public static void showMenu() {
        show(MENU_FXML);
    }

    public static void showLobby() {
        show(LOBBY_FXML);
    }

    public static void showGame() {
        show(GAME_FXML);
    }

    public static void showHowToPlay() {
        if (currentScreen == null || HOW_TO_PLAY_FXML.equals(currentScreen)) {
            howToPlayReturnScreen = MENU_FXML;
        } else {
            howToPlayReturnScreen = currentScreen;
        }
        show(HOW_TO_PLAY_FXML);
    }

    public static void returnFromHowToPlay() {
        String target = howToPlayReturnScreen == null ? MENU_FXML : howToPlayReturnScreen;
        howToPlayReturnScreen = null;
        show(target);
    }

    public static void showResult() {
        show(RESULT_FXML);
    }

    public static void exitApplication() {
        if (sessionContext != null) {
            sessionContext.close();
        }
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
            if (sessionContext != null) {
                sessionContext.setSnapshotListener(null);
                sessionContext.setMessageListener(null);
            }
            FXMLLoader loader = new FXMLLoader(SceneManager.class.getResource("/fxml/" + fxmlName));
            loader.setControllerFactory(SceneManager::createController);
            Parent root = loader.load();
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
            currentScreen = fxmlName;
            stage.show();
        } catch (IOException | RuntimeException exception) {
            throw new IllegalStateException("Unable to load UI screen: " + fxmlName, exception);
        }
    }

    private static Object createController(Class<?> controllerType) {
        try {
            Constructor<?> constructor = controllerType.getDeclaredConstructor(UiSessionContext.class);
            constructor.setAccessible(true);
            return constructor.newInstance(sessionContext());
        } catch (NoSuchMethodException ignored) {
            // Fall through to the no-arg controller used by static screens.
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Unable to create controller: " + controllerType.getName(), exception);
        }

        try {
            Constructor<?> constructor = controllerType.getDeclaredConstructor();
            constructor.setAccessible(true);
            return constructor.newInstance();
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Unable to create controller: " + controllerType.getName(), exception);
        }
    }
}
