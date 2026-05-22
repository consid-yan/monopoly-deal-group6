// EndTurnHandler.java
package ie.ucd.bdic.group6.facade.handler;

import ie.ucd.bdic.group6.command.EndTurnCommand;
import ie.ucd.bdic.group6.core.engine.ActionExecutor;
import ie.ucd.bdic.group6.facade.model.GameResult;

public class EndTurnHandler extends AbstractCommandHandler<EndTurnCommand> {
    public EndTurnHandler(ActionExecutor actionExecutor) {
        super(actionExecutor);
    }

    @Override
    public Class<EndTurnCommand> commandType() {
        return EndTurnCommand.class;
    }

    @Override
    protected GameResult execute(EndTurnCommand command) {
        return GameResult.success(actionExecutor.executeEndTurn(command));
    }
}
