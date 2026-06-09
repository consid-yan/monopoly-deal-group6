package ie.ucd.bdic.group6.facade.handler;

import ie.ucd.bdic.group6.command.connection.ProcessDisconnectTimeoutsCommand;
import ie.ucd.bdic.group6.core.engine.ActionExecutor;
import ie.ucd.bdic.group6.facade.model.CommandResult;

public class ProcessDisconnectTimeoutsHandler extends AbstractCommandHandler<ProcessDisconnectTimeoutsCommand> {
    public ProcessDisconnectTimeoutsHandler(ActionExecutor actionExecutor) {
        super(actionExecutor);
    }

    @Override
    public Class<ProcessDisconnectTimeoutsCommand> commandType() {
        return ProcessDisconnectTimeoutsCommand.class;
    }

    @Override
    protected CommandResult execute(ProcessDisconnectTimeoutsCommand command) {
        return CommandResult.success(actionExecutor.executeProcessDisconnectTimeouts(command));
    }
}
