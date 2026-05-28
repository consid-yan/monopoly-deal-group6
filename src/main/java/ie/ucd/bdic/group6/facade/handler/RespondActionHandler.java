package ie.ucd.bdic.group6.facade.handler;

import ie.ucd.bdic.group6.command.RespondActionCommand;
import ie.ucd.bdic.group6.core.engine.ActionExecutor;
import ie.ucd.bdic.group6.facade.model.GameResult;

public class RespondActionHandler extends AbstractCommandHandler<RespondActionCommand> {
    public RespondActionHandler(ActionExecutor actionExecutor) {
        super(actionExecutor);
    }

    @Override
    public Class<RespondActionCommand> commandType() {
        return RespondActionCommand.class;
    }

    @Override
    protected GameResult execute(RespondActionCommand command) {
        return GameResult.success(actionExecutor.executeRespondAction(command));
    }
}
