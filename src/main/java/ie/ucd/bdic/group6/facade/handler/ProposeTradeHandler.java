// ProposeTradeHandler.java
package ie.ucd.bdic.group6.facade.handler;

import ie.ucd.bdic.group6.command.trade.ProposeTradeCommand;
import ie.ucd.bdic.group6.core.engine.ActionExecutor;
import ie.ucd.bdic.group6.facade.model.CommandResult;

public class ProposeTradeHandler extends AbstractCommandHandler<ProposeTradeCommand> {
    public ProposeTradeHandler(ActionExecutor actionExecutor) {
        super(actionExecutor);
    }

    @Override
    public Class<ProposeTradeCommand> commandType() {
        return ProposeTradeCommand.class;
    }

    @Override
    protected CommandResult execute(ProposeTradeCommand command) {
        return CommandResult.success(actionExecutor.executeProposeTrade(command));
    }
}
