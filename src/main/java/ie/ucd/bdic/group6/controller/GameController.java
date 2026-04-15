package ie.ucd.bdic.group6.controller;

import ie.ucd.bdic.group6.controller.dto.ControllerRequest;
import ie.ucd.bdic.group6.controller.mapper.RequestMapper;
import ie.ucd.bdic.group6.facade.model.GameResult;
import ie.ucd.bdic.group6.facade.Facade;

public class GameController {
    private final Facade gameFacade;
    private final RequestMapper requestMapper;

    public GameController(Facade facade, RequestMapper mapper) {
        gameFacade = facade;
        requestMapper = mapper;
    }

    public GameResult startGame(ControllerRequest request) {
        return gameFacade.execute(requestMapper.toStartGameCommand(request));
    }

    public GameResult playCard(ControllerRequest request) {
        return gameFacade.execute(requestMapper.toPlayCardCommand(request));
    }

    public GameResult endTurn(ControllerRequest request) {
        return gameFacade.execute(requestMapper.toEndTurnCommand(request));
    }

    public GameResult trade(ControllerRequest request) {
        return gameFacade.execute(requestMapper.toTradeCommand(request));
    }
}
