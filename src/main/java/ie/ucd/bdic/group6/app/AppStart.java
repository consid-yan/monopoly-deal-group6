package ie.ucd.bdic.group6.app;

import ie.ucd.bdic.group6.ui.SceneManager;
import javafx.application.Application;
import javafx.stage.Stage;

public class AppStart {

    private AppConfig appConfig;

    public void init() {
        appConfig = new AppConfig();
    }

    public void start(Stage primaryStage) {
        SceneManager.initialize(primaryStage, appConfig);
    }

    public static void main(String[] args) {
        Application.launch(FxApplication.class, args);
    }

    public static final class FxApplication extends Application {

        private final AppStart appStart = new AppStart();

        @Override
        public void init() {
            appStart.init();
        }

        @Override
        public void start(Stage primaryStage) {
            appStart.start(primaryStage);
        }
    }
}
