package ie.ucd.bdic.group6.facade.handler;

import ie.ucd.bdic.group6.command.PlayCardCommand;
import ie.ucd.bdic.group6.core.engine.ActionExecutor;
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
        return GameResult.notImplemented("PLAY_CARD");
    }
}
