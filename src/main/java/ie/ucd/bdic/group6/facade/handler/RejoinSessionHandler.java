// RejoinSessionHandler.java
package ie.ucd.bdic.group6.facade.handler;

import ie.ucd.bdic.group6.command.RejoinSessionCommand;
import ie.ucd.bdic.group6.core.engine.ActionExecutor;
import ie.ucd.bdic.group6.facade.model.CommandResult;

public class RejoinSessionHandler extends AbstractCommandHandler<RejoinSessionCommand> {
    public RejoinSessionHandler(ActionExecutor actionExecutor) {
        super(actionExecutor);
    }

    @Override
    public Class<RejoinSessionCommand> commandType() {
        return RejoinSessionCommand.class;
    }

    @Override
    protected CommandResult execute(RejoinSessionCommand command) {
        return CommandResult.success(actionExecutor.executeRejoinSession(command));
    }
}
