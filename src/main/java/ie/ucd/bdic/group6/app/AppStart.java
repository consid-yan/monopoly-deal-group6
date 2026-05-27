package ie.ucd.bdic.group6.app;

import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;

public class AppStart extends Application {

    private AppConfig appConfig;

    @Override
    public void init() {
        appConfig = new AppConfig();
    }

    @Override
    public void start(Stage primaryStage) {
        primaryStage.setTitle("Monopoly Deal");
        StackPane root = new StackPane(new Label("Monopoly Deal — MVP"));
        primaryStage.setScene(new Scene(root, 800, 600));
        primaryStage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
