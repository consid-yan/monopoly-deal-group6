package ie.ucd.bdic.group6.facade;

import ie.ucd.bdic.group6.command.Command;
import ie.ucd.bdic.group6.core.engine.ActionExecutor;
import ie.ucd.bdic.group6.core.engine.GameEngine;
import ie.ucd.bdic.group6.facade.handler.CommandHandler;
import ie.ucd.bdic.group6.facade.handler.CreateSessionHandler;
import ie.ucd.bdic.group6.facade.handler.DiscardCardsHandler;
import ie.ucd.bdic.group6.facade.handler.EndTurnHandler;
import ie.ucd.bdic.group6.facade.handler.JoinSessionHandler;
import ie.ucd.bdic.group6.facade.handler.PayDebtHandler;
import ie.ucd.bdic.group6.facade.handler.PlayCardHandler;
import ie.ucd.bdic.group6.facade.handler.PlayerDisconnectedHandler;
import ie.ucd.bdic.group6.facade.handler.ProcessDisconnectTimeoutsHandler;
import ie.ucd.bdic.group6.facade.handler.ProposeTradeHandler;
import ie.ucd.bdic.group6.facade.handler.RejoinSessionHandler;
import ie.ucd.bdic.group6.facade.handler.RespondActionHandler;
import ie.ucd.bdic.group6.facade.handler.RespondTradeHandler;
import ie.ucd.bdic.group6.facade.handler.StartGameHandler;
import ie.ucd.bdic.group6.facade.model.GameResult;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

public class GameFacade implements Facade {
    private final Map<Class<? extends Command>, CommandHandler<? extends Command>> handlers;

    public GameFacade() {
        this(new GameEngine());
    }

    public GameFacade(ActionExecutor actionExecutor) {
        this(List.of(
                new CreateSessionHandler(actionExecutor),
                new JoinSessionHandler(actionExecutor),
                new StartGameHandler(actionExecutor),
                new PlayCardHandler(actionExecutor),
                new PayDebtHandler(actionExecutor),
                new ProposeTradeHandler(actionExecutor),
                new RespondTradeHandler(actionExecutor),
                new RespondActionHandler(actionExecutor),
                new DiscardCardsHandler(actionExecutor),
                new EndTurnHandler(actionExecutor),
                new RejoinSessionHandler(actionExecutor),
                new PlayerDisconnectedHandler(actionExecutor),
                new ProcessDisconnectTimeoutsHandler(actionExecutor)
        ));
    }

    public GameFacade(List<CommandHandler<? extends Command>> handlers) {
        this.handlers = handlers.stream()
                .collect(Collectors.toUnmodifiableMap(
                        CommandHandler::commandType,
                        Function.identity()
                ));
    }

    @Override
    public GameResult execute(Command command) {
        if (command == null) {
            return GameResult.failure("COMMAND_REQUIRED", "Command must not be null");
        }

        CommandHandler<? extends Command> handler = handlers.get(command.getClass());
        if (handler == null) {
            return GameResult.failure(
                    "UNSUPPORTED_COMMAND",
                    "Unsupported command type: " + command.getCommandType()
            );
        }

        return dispatch(handler, command);
    }

    @SuppressWarnings("unchecked")
    private <C extends Command> GameResult dispatch(CommandHandler<C> handler, Command command) {
        return handler.handle((C) command);
    }
}
