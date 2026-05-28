package ie.ucd.bdic.group6.facade.handler;

import ie.ucd.bdic.group6.command.ProcessDisconnectTimeoutsCommand;
import ie.ucd.bdic.group6.core.engine.ActionExecutor;
import ie.ucd.bdic.group6.facade.model.GameResult;

public class ProcessDisconnectTimeoutsHandler extends AbstractCommandHandler<ProcessDisconnectTimeoutsCommand> {
    public ProcessDisconnectTimeoutsHandler(ActionExecutor actionExecutor) {
        super(actionExecutor);
    }

    @Override
    public Class<ProcessDisconnectTimeoutsCommand> commandType() {
        return ProcessDisconnectTimeoutsCommand.class;
    }

    @Override
    protected GameResult execute(ProcessDisconnectTimeoutsCommand command) {
        return GameResult.success(actionExecutor.executeProcessDisconnectTimeouts(command));
    }
}
