package ie.ucd.bdic.group6.facade;

import ie.ucd.bdic.group6.command.Command;
import ie.ucd.bdic.group6.command.action.RespondActionCommand;
import ie.ucd.bdic.group6.command.card.DiscardCardCommand;
import ie.ucd.bdic.group6.command.card.PlayCardCommand;
import ie.ucd.bdic.group6.command.connection.PlayerDisconnectedCommand;
import ie.ucd.bdic.group6.command.connection.ProcessDisconnectTimeoutsCommand;
import ie.ucd.bdic.group6.command.debt.PayDebtCommand;
import ie.ucd.bdic.group6.command.game.EndTurnCommand;
import ie.ucd.bdic.group6.command.game.StartGameCommand;
import ie.ucd.bdic.group6.command.session.CreateSessionCommand;
import ie.ucd.bdic.group6.command.session.JoinSessionCommand;
import ie.ucd.bdic.group6.command.session.RejoinSessionCommand;
import ie.ucd.bdic.group6.command.trade.ProposeTradeCommand;
import ie.ucd.bdic.group6.command.trade.RespondTradeCommand;
import ie.ucd.bdic.group6.core.engine.ActionExecutor;
import ie.ucd.bdic.group6.core.engine.GameEngine;
import ie.ucd.bdic.group6.exception.GameException;
import ie.ucd.bdic.group6.facade.model.CommandResult;

import java.util.Map;
import java.util.function.Function;

public class GameFacade implements Facade {
    private final Map<Class<? extends Command>, Function<Command, CommandResult>> handlers;

    public GameFacade() {
        this(new GameEngine());
    }

    public GameFacade(ActionExecutor actionExecutor) {
        handlers = Map.ofEntries(
                handler(CreateSessionCommand.class,
                        command -> CommandResult.success(actionExecutor.executeCreateSession(command))),
                handler(JoinSessionCommand.class,
                        command -> CommandResult.success(actionExecutor.executeJoinSession(command))),
                handler(StartGameCommand.class,
                        command -> CommandResult.success(actionExecutor.executeStartGame(command))),
                handler(PlayCardCommand.class,
                        command -> CommandResult.success(actionExecutor.executePlayCard(command))),
                handler(PayDebtCommand.class,
                        command -> CommandResult.success(actionExecutor.executePayDebt(command))),
                handler(ProposeTradeCommand.class,
                        command -> CommandResult.success(actionExecutor.executeProposeTrade(command))),
                handler(RespondTradeCommand.class,
                        command -> CommandResult.success(actionExecutor.executeRespondTrade(command))),
                handler(RespondActionCommand.class,
                        command -> CommandResult.success(actionExecutor.executeRespondAction(command))),
                handler(DiscardCardCommand.class,
                        command -> CommandResult.success(actionExecutor.executeDiscardCards(command))),
                handler(EndTurnCommand.class,
                        command -> CommandResult.success(actionExecutor.executeEndTurn(command))),
                handler(RejoinSessionCommand.class,
                        command -> CommandResult.success(actionExecutor.executeRejoinSession(command))),
                handler(PlayerDisconnectedCommand.class,
                        command -> CommandResult.success(actionExecutor.executePlayerDisconnected(command))),
                handler(ProcessDisconnectTimeoutsCommand.class,
                        command -> CommandResult.success(actionExecutor.executeProcessDisconnectTimeouts(command)))
        );
    }

    /**
     * Dispatches registered command classes and translates execution failures into command results.
     */
    @Override
    public CommandResult execute(Command command) {
        if (command == null) {
            return CommandResult.failure("COMMAND_REQUIRED", "Command must not be null");
        }

        Function<Command, CommandResult> handler = handlers.get(command.getClass());
        if (handler == null) {
            return CommandResult.failure(
                    "UNSUPPORTED_COMMAND",
                    "Unsupported command type: " + command.getCommandType()
            );
        }

        try {
            return handler.apply(command);
        } catch (GameException e) {
            return CommandResult.failure(e.getErrorCode(), e.getMessage());
        } catch (Exception e) {
            return CommandResult.failure("INTERNAL_ERROR",
                    "Failed to execute " + command.getCommandType() + ": " + e.getMessage());
        }
    }

    private static <C extends Command> Map.Entry<Class<? extends Command>, Function<Command, CommandResult>> handler(
            Class<C> commandType, Function<C, CommandResult> execute) {
        return Map.entry(commandType, command -> execute.apply(commandType.cast(command)));
    }
}
