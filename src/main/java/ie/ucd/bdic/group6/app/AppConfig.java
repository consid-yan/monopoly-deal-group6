package ie.ucd.bdic.group6.app;

import ie.ucd.bdic.group6.controller.GameController;
import ie.ucd.bdic.group6.controller.mapper.RequestMapper;
import ie.ucd.bdic.group6.core.engine.GameEngine;
import ie.ucd.bdic.group6.facade.GameFacade;
import ie.ucd.bdic.group6.network.server.GameServer;

public class AppConfig {
    private final GameEngine gameEngine = new GameEngine();
    private final GameFacade gameFacade = new GameFacade(gameEngine);
    private final RequestMapper requestMapper = new RequestMapper();

    public GameFacade gameFacade() {
        return gameFacade;
    }

    public RequestMapper requestMapper() {
        return requestMapper;
    }

    public GameController gameController() {
        return new GameController(gameFacade, requestMapper);
    }

    public GameServer gameServer(int port) {
        return new GameServer(gameController(), port);
    }
}
