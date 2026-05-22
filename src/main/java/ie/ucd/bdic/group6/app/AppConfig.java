package ie.ucd.bdic.group6.app;

import ie.ucd.bdic.group6.controller.GameController;
import ie.ucd.bdic.group6.controller.mapper.RequestMapper;
import ie.ucd.bdic.group6.core.engine.ActionExecutor;
import ie.ucd.bdic.group6.core.engine.GameEngine;
import ie.ucd.bdic.group6.facade.GameFacade;

public class AppConfig {
    private final ActionExecutor gameEngine = new GameEngine();

    public GameFacade gameFacade() {
        return new GameFacade(gameEngine);
    }

    public RequestMapper requestMapper() {
        return new RequestMapper();
    }

    public GameController gameController() {
        return new GameController(gameFacade(), requestMapper());
    }
}
