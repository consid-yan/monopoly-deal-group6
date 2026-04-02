package ie.ucd.bdic.group6.facade.handler;

import ie.ucd.bdic.group6.command.PlayCardCommand;
import ie.ucd.bdic.group6.core.card.ActionExecutionResult;
import ie.ucd.bdic.group6.core.card.ActionExecutor;
import ie.ucd.bdic.group6.facade.model.GameResult;

public class PlayCardHandler implements CommandHandler<PlayCardCommand> {
    private final ActionExecutor actionExecutor;

    public PlayCardHandler() {
        this(new ActionExecutor());
    }

    public PlayCardHandler(ActionExecutor actionExecutor) {
        this.actionExecutor = actionExecutor;
    }

    @Override
    public Class<PlayCardCommand> commandType() {
        return PlayCardCommand.class;
    }

    @Override
    public GameResult handle(PlayCardCommand command) {
        ActionExecutionResult result = actionExecutor.executePlayCard(command.payload());
        if (result.success()) {
            return GameResult.success(result.message());
        }
        return GameResult.failure(result.message());
    }
}
