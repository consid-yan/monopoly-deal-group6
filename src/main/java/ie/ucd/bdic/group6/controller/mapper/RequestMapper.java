package ie.ucd.bdic.group6.controller.mapper;

import ie.ucd.bdic.group6.command.EndTurnCommand;
import ie.ucd.bdic.group6.command.PlayCardCommand;
import ie.ucd.bdic.group6.command.StartGameCommand;
import ie.ucd.bdic.group6.command.TradeCommand;
import ie.ucd.bdic.group6.controller.dto.ControllerRequest;

import java.util.Map;

public class RequestMapper {
    public StartGameCommand toStartGameCommand(ControllerRequest request) {
        return new StartGameCommand(safeData(request));
    }

    public PlayCardCommand toPlayCardCommand(ControllerRequest request) {
        return new PlayCardCommand(safeData(request));
    }

    public EndTurnCommand toEndTurnCommand(ControllerRequest request) {
        return new EndTurnCommand(safeData(request));
    }

    public TradeCommand toTradeCommand(ControllerRequest request) {
        return new TradeCommand(safeData(request));
    }

    private Map<String, Object> safeData(ControllerRequest request) {
        if (request == null || request.data() == null) {
            return Map.of();
        }
        return request.data();
    }
}
