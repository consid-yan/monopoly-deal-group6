// PlayCardHandler.java
package ie.ucd.bdic.group6.facade.handler;

import ie.ucd.bdic.group6.command.card.PlayCardCommand;
import ie.ucd.bdic.group6.core.engine.ActionExecutor;
import ie.ucd.bdic.group6.facade.model.CommandResult;

public class PlayCardHandler extends AbstractCommandHandler<PlayCardCommand> {
    public PlayCardHandler(ActionExecutor actionExecutor) {
        super(actionExecutor);
    }

    @Override
    public Class<PlayCardCommand> commandType() {
        return PlayCardCommand.class;
    }

    @Override
    protected CommandResult execute(PlayCardCommand command) {
        return CommandResult.success(actionExecutor.executePlayCard(command));
    }
}
