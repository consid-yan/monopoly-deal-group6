// ProposeTradeHandler.java
package ie.ucd.bdic.group6.facade.handler;

import ie.ucd.bdic.group6.command.ProposeTradeCommand;
import ie.ucd.bdic.group6.core.engine.ActionExecutor;
import ie.ucd.bdic.group6.facade.model.GameResult;

public class ProposeTradeHandler extends AbstractCommandHandler<ProposeTradeCommand> {
    public ProposeTradeHandler(ActionExecutor actionExecutor) {
        super(actionExecutor);
    }

    @Override
    public Class<ProposeTradeCommand> commandType() {
        return ProposeTradeCommand.class;
    }

    @Override
    protected GameResult execute(ProposeTradeCommand command) {
        return GameResult.success(actionExecutor.executeProposeTrade(command));
    }
}
