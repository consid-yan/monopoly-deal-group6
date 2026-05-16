package ie.ucd.bdic.group6.facade;

import ie.ucd.bdic.group6.command.Command;
import ie.ucd.bdic.group6.facade.handler.*;
import ie.ucd.bdic.group6.facade.model.GameResult;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

public class GameFacade implements Facade {
    private final Map<Class<? extends Command>, CommandHandler<? extends Command>> handlers;

    // 默认构造器：自动注册所有Handler
    public GameFacade() {
        this(List.of(
                new CreateSessionHandler(),
                new JoinSessionHandler(),
                new StartGameHandler(),
                new PlayCardHandler(),
                new PayDebtHandler(),
                new ProposeTradeHandler(),
                new RespondTradeHandler(),
                new DiscardCardsHandler(),
                new EndTurnHandler(),
                new RejoinSessionHandler()
        ));
    }

    // 测试用构造器：支持注入Mock Handler
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