// DiscardCardsHandler.java
package ie.ucd.bdic.group6.facade.handler;

import ie.ucd.bdic.group6.command.DiscardCardsCommand;
import ie.ucd.bdic.group6.core.engine.ActionExecutor;
import ie.ucd.bdic.group6.facade.model.GameResult;

public class DiscardCardsHandler extends AbstractCommandHandler<DiscardCardsCommand> {
    public DiscardCardsHandler(ActionExecutor actionExecutor) {
        super(actionExecutor);
    }

    @Override
    public Class<DiscardCardsCommand> commandType() {
        return DiscardCardsCommand.class;
    }

    @Override
    protected GameResult execute(DiscardCardsCommand command) {
        return GameResult.success(actionExecutor.executeDiscardCards(command));
    }
}
