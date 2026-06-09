// DiscardCardsHandler.java
package ie.ucd.bdic.group6.facade.handler;

import ie.ucd.bdic.group6.command.card.DiscardCardCommand;
import ie.ucd.bdic.group6.core.engine.ActionExecutor;
import ie.ucd.bdic.group6.facade.model.CommandResult;

public class DiscardCardsHandler extends AbstractCommandHandler<DiscardCardCommand> {
    public DiscardCardsHandler(ActionExecutor actionExecutor) {
        super(actionExecutor);
    }

    @Override
    public Class<DiscardCardCommand> commandType() {
        return DiscardCardCommand.class;
    }

    @Override
    protected CommandResult execute(DiscardCardCommand command) {
        return CommandResult.success(actionExecutor.executeDiscardCards(command));
    }
}
