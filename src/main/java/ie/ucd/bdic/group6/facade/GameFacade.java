package ie.ucd.bdic.group6.facade;

import ie.ucd.bdic.group6.command.Command;
import ie.ucd.bdic.group6.facade.handler.EndTurnHandler;
import ie.ucd.bdic.group6.facade.handler.CommandHandler;
import ie.ucd.bdic.group6.facade.handler.PlayCardHandler;
import ie.ucd.bdic.group6.facade.handler.StartGameHandler;
import ie.ucd.bdic.group6.facade.handler.TradeHandler;
import ie.ucd.bdic.group6.facade.model.GameResult;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

public class GameFacade implements Facade {
    private final Map<Class<? extends Command>, CommandHandler<? extends Command>> handlers;

    public GameFacade() {
        this(List.of(new StartGameHandler(), new PlayCardHandler(), new EndTurnHandler(), new TradeHandler()));
    }

    public GameFacade(List<CommandHandler<? extends Command>> handlers) {
        this.handlers = handlers.stream()
                .collect(Collectors.toUnmodifiableMap(CommandHandler::commandType, Function.identity()));
    }

    @Override
    public GameResult execute(Command command) {
        if (command == null) {
            return GameResult.failure("COMMAND_REQUIRED");
        }

        CommandHandler<? extends Command> rawHandler = handlers.get(command.getClass());
        if (rawHandler == null) {
            return GameResult.failure("UNSUPPORTED_COMMAND:" + command.getClass().getSimpleName());
        }

        return dispatch(rawHandler, command);
    }

    private <C extends Command> GameResult dispatch(CommandHandler<C> handler, Command command) {
        return handler.handle(handler.commandType().cast(command));
    }
}
