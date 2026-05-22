// PlayCardHandler.java
package ie.ucd.bdic.group6.facade.handler;

import ie.ucd.bdic.group6.command.PlayCardCommand;
import ie.ucd.bdic.group6.core.engine.ActionExecutor;
import ie.ucd.bdic.group6.facade.model.GameResult;

public class PlayCardHandler extends AbstractCommandHandler<PlayCardCommand> {
    public PlayCardHandler(ActionExecutor actionExecutor) {
        super(actionExecutor);
    }

    @Override
    public Class<PlayCardCommand> commandType() {
        return PlayCardCommand.class;
    }

    @Override
    protected GameResult execute(PlayCardCommand command) {
        return GameResult.success(actionExecutor.executePlayCard(command));
    }
}
