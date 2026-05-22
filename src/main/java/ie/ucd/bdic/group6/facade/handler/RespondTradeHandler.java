// RespondTradeHandler.java
package ie.ucd.bdic.group6.facade.handler;

import ie.ucd.bdic.group6.command.RespondTradeCommand;
import ie.ucd.bdic.group6.core.engine.ActionExecutor;
import ie.ucd.bdic.group6.facade.model.GameResult;

public class RespondTradeHandler extends AbstractCommandHandler<RespondTradeCommand> {
    public RespondTradeHandler(ActionExecutor actionExecutor) {
        super(actionExecutor);
    }

    @Override
    public Class<RespondTradeCommand> commandType() {
        return RespondTradeCommand.class;
    }

    @Override
    protected GameResult execute(RespondTradeCommand command) {
        return GameResult.success(actionExecutor.executeRespondTrade(command));
    }
}
