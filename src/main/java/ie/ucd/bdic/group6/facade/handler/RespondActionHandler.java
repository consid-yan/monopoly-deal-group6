package ie.ucd.bdic.group6.facade.handler;

import ie.ucd.bdic.group6.command.action.RespondActionCommand;
import ie.ucd.bdic.group6.core.engine.ActionExecutor;
import ie.ucd.bdic.group6.facade.model.CommandResult;

public class RespondActionHandler extends AbstractCommandHandler<RespondActionCommand> {
    public RespondActionHandler(ActionExecutor actionExecutor) {
        super(actionExecutor);
    }

    @Override
    public Class<RespondActionCommand> commandType() {
        return RespondActionCommand.class;
    }

    @Override
    protected CommandResult execute(RespondActionCommand command) {
        return CommandResult.success(actionExecutor.executeRespondAction(command));
    }
}
