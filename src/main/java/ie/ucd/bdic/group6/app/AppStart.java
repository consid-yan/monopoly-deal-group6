package ie.ucd.bdic.group6.app;

import ie.ucd.bdic.group6.controller.GameController;

public class AppStart {
    public static void main(String[] args) {
        AppConfig appConfig = new AppConfig();
        GameController gameController = appConfig.gameController();

        if (gameController == null) {
            throw new IllegalStateException("Controller wiring failed");
        }
    }
}
