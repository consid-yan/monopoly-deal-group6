package ie.ucd.bdic.group6.controller;

import ie.ucd.bdic.group6.controller.dto.ControllerRequest;
import ie.ucd.bdic.group6.controller.mapper.RequestMapper;
import ie.ucd.bdic.group6.facade.Facade;
import ie.ucd.bdic.group6.facade.model.CommandResult;

public class GameController {
    private final Facade gameFacade;
    private final RequestMapper requestMapper;

    public GameController(Facade facade, RequestMapper mapper) {
        this.gameFacade = facade;
        this.requestMapper = mapper;
    }

    public CommandResult createSession(ControllerRequest request) {
        return gameFacade.execute(requestMapper.toCreateSessionCommand(request));
    }

    public CommandResult joinSession(ControllerRequest request) {
        return gameFacade.execute(requestMapper.toJoinSessionCommand(request));
    }

    public CommandResult startGame(ControllerRequest request) {
        return gameFacade.execute(requestMapper.toStartGameCommand(request));
    }

    public CommandResult playCard(ControllerRequest request) {
        return gameFacade.execute(requestMapper.toPlayCardCommand(request));
    }

    public CommandResult payDebt(ControllerRequest request) {
        return gameFacade.execute(requestMapper.toPayDebtCommand(request));
    }

    public CommandResult proposeTrade(ControllerRequest request) {
        return gameFacade.execute(requestMapper.toProposeTradeCommand(request));
    }

    public CommandResult respondTrade(ControllerRequest request) {
        return gameFacade.execute(requestMapper.toRespondTradeCommand(request));
    }

    public CommandResult respondAction(ControllerRequest request) {
        return gameFacade.execute(requestMapper.toRespondActionCommand(request));
    }

    public CommandResult discardCards(ControllerRequest request) {
        return gameFacade.execute(requestMapper.toDiscardCardsCommand(request));
    }

    public CommandResult endTurn(ControllerRequest request) {
        return gameFacade.execute(requestMapper.toEndTurnCommand(request));
    }

    public CommandResult rejoinSession(ControllerRequest request) {
        return gameFacade.execute(requestMapper.toRejoinSessionCommand(request));
    }

    public CommandResult playerDisconnected(ControllerRequest request) {
        return gameFacade.execute(requestMapper.toPlayerDisconnectedCommand(request));
    }

    public CommandResult processDisconnectTimeouts(ControllerRequest request) {
        return gameFacade.execute(requestMapper.toProcessDisconnectTimeoutsCommand(request));
    }
}
