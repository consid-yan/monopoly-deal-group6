// DiscardCardsHandler.java
package ie.ucd.bdic.group6.facade.handler;

import ie.ucd.bdic.group6.command.DiscardCardsCommand;
import ie.ucd.bdic.group6.core.engine.ActionExecutor;
import ie.ucd.bdic.group6.facade.model.CommandResult;

public class DiscardCardsHandler extends AbstractCommandHandler<DiscardCardsCommand> {
    public DiscardCardsHandler(ActionExecutor actionExecutor) {
        super(actionExecutor);
    }

    @Override
    public Class<DiscardCardsCommand> commandType() {
        return DiscardCardsCommand.class;
    }

    @Override
    protected CommandResult execute(DiscardCardsCommand command) {
        return CommandResult.success(actionExecutor.executeDiscardCards(command));
    }
}
