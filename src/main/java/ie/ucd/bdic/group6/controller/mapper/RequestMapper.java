package ie.ucd.bdic.group6.controller.mapper;

import ie.ucd.bdic.group6.command.card.DiscardCardCommand;
import ie.ucd.bdic.group6.command.connection.PlayerDisconnectedCommand;
import ie.ucd.bdic.group6.command.connection.ProcessDisconnectTimeoutsCommand;
import ie.ucd.bdic.group6.command.game.EndTurnCommand;
import ie.ucd.bdic.group6.command.debt.PayDebtCommand;
import ie.ucd.bdic.group6.command.card.PlayCardCommand;
import ie.ucd.bdic.group6.command.trade.ProposeTradeCommand;
import ie.ucd.bdic.group6.command.action.RespondActionCommand;
import ie.ucd.bdic.group6.command.trade.RespondTradeCommand;
import ie.ucd.bdic.group6.command.session.CreateSessionCommand;
import ie.ucd.bdic.group6.command.session.JoinSessionCommand;
import ie.ucd.bdic.group6.command.session.RejoinSessionCommand;
import ie.ucd.bdic.group6.command.game.StartGameCommand;
import ie.ucd.bdic.group6.controller.dto.ControllerRequest;

import java.util.Map;

public class RequestMapper {
    public CreateSessionCommand toCreateSessionCommand(ControllerRequest request) {
        return new CreateSessionCommand(safeData(request));
    }

    public JoinSessionCommand toJoinSessionCommand(ControllerRequest request) {
        return new JoinSessionCommand(safeData(request));
    }

    public StartGameCommand toStartGameCommand(ControllerRequest request) {
        return new StartGameCommand(safeData(request));
    }

    public PlayCardCommand toPlayCardCommand(ControllerRequest request) {
        return new PlayCardCommand(safeData(request));
    }

    public PayDebtCommand toPayDebtCommand(ControllerRequest request) {
        return new PayDebtCommand(safeData(request));
    }

    public ProposeTradeCommand toProposeTradeCommand(ControllerRequest request) {
        return new ProposeTradeCommand(safeData(request));
    }

    public RespondTradeCommand toRespondTradeCommand(ControllerRequest request) {
        return new RespondTradeCommand(safeData(request));
    }

    public RespondActionCommand toRespondActionCommand(ControllerRequest request) {
        return new RespondActionCommand(safeData(request));
    }

    public DiscardCardCommand toDiscardCardsCommand(ControllerRequest request) {
        return new DiscardCardCommand(safeData(request));
    }

    public EndTurnCommand toEndTurnCommand(ControllerRequest request) {
        return new EndTurnCommand(safeData(request));
    }

    public RejoinSessionCommand toRejoinSessionCommand(ControllerRequest request) {
        return new RejoinSessionCommand(safeData(request));
    }

    public PlayerDisconnectedCommand toPlayerDisconnectedCommand(ControllerRequest request) {
        return new PlayerDisconnectedCommand(safeData(request));
    }

    public ProcessDisconnectTimeoutsCommand toProcessDisconnectTimeoutsCommand(ControllerRequest request) {
        return new ProcessDisconnectTimeoutsCommand(safeData(request));
    }

    private Map<String, Object> safeData(ControllerRequest request) {
        if (request == null || request.data() == null) {
            return Map.of();
        }
        return request.data();
    }
}
