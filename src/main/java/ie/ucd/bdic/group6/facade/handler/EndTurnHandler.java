// EndTurnHandler.java
package ie.ucd.bdic.group6.facade.handler;

import ie.ucd.bdic.group6.command.game.EndTurnCommand;
import ie.ucd.bdic.group6.core.engine.ActionExecutor;
import ie.ucd.bdic.group6.facade.model.CommandResult;

public class EndTurnHandler extends AbstractCommandHandler<EndTurnCommand> {
    public EndTurnHandler(ActionExecutor actionExecutor) {
        super(actionExecutor);
    }

    @Override
    public Class<EndTurnCommand> commandType() {
        return EndTurnCommand.class;
    }

    @Override
    protected CommandResult execute(EndTurnCommand command) {
        return CommandResult.success(actionExecutor.executeEndTurn(command));
    }
}
