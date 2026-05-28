package ie.ucd.bdic.group6.controller;

import ie.ucd.bdic.group6.controller.dto.ControllerRequest;
import ie.ucd.bdic.group6.controller.mapper.RequestMapper;
import ie.ucd.bdic.group6.facade.Facade;
import ie.ucd.bdic.group6.facade.model.GameResult;

public class GameController {
    private final Facade gameFacade;
    private final RequestMapper requestMapper;

    public GameController(Facade facade, RequestMapper mapper) {
        this.gameFacade = facade;
        this.requestMapper = mapper;
    }

    public GameResult createSession(ControllerRequest request) {
        return gameFacade.execute(requestMapper.toCreateSessionCommand(request));
    }

    public GameResult joinSession(ControllerRequest request) {
        return gameFacade.execute(requestMapper.toJoinSessionCommand(request));
    }

    public GameResult startGame(ControllerRequest request) {
        return gameFacade.execute(requestMapper.toStartGameCommand(request));
    }

    public GameResult playCard(ControllerRequest request) {
        return gameFacade.execute(requestMapper.toPlayCardCommand(request));
    }

    public GameResult payDebt(ControllerRequest request) {
        return gameFacade.execute(requestMapper.toPayDebtCommand(request));
    }

    public GameResult proposeTrade(ControllerRequest request) {
        return gameFacade.execute(requestMapper.toProposeTradeCommand(request));
    }

    public GameResult respondTrade(ControllerRequest request) {
        return gameFacade.execute(requestMapper.toRespondTradeCommand(request));
    }

    public GameResult respondAction(ControllerRequest request) {
        return gameFacade.execute(requestMapper.toRespondActionCommand(request));
    }

    public GameResult discardCards(ControllerRequest request) {
        return gameFacade.execute(requestMapper.toDiscardCardsCommand(request));
    }

    public GameResult endTurn(ControllerRequest request) {
        return gameFacade.execute(requestMapper.toEndTurnCommand(request));
    }

    public GameResult rejoinSession(ControllerRequest request) {
        return gameFacade.execute(requestMapper.toRejoinSessionCommand(request));
    }

    public GameResult playerDisconnected(ControllerRequest request) {
        return gameFacade.execute(requestMapper.toPlayerDisconnectedCommand(request));
    }

    public GameResult processDisconnectTimeouts(ControllerRequest request) {
        return gameFacade.execute(requestMapper.toProcessDisconnectTimeoutsCommand(request));
    }
}
