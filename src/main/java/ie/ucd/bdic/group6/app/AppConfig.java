package ie.ucd.bdic.group6.app;

import ie.ucd.bdic.group6.controller.GameController;
import ie.ucd.bdic.group6.controller.model.RequestMapper;
import ie.ucd.bdic.group6.facade.GameFacade;

public class AppConfig {
    public GameFacade gameFacade() {
        return new GameFacade();
    }

    public RequestMapper requestMapper() {
        return new RequestMapper();
    }

    public GameController gameController() {
        return new GameController(gameFacade(), requestMapper());
    }
}
