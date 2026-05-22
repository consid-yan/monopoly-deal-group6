// CreateSessionHandler.java
package ie.ucd.bdic.group6.facade.handler;

import ie.ucd.bdic.group6.command.CreateSessionCommand;
import ie.ucd.bdic.group6.core.engine.ActionExecutor;
import ie.ucd.bdic.group6.facade.model.GameResult;

public class CreateSessionHandler extends AbstractCommandHandler<CreateSessionCommand> {
    public CreateSessionHandler(ActionExecutor actionExecutor) {
        super(actionExecutor);
    }

    @Override
    public Class<CreateSessionCommand> commandType() {
        return CreateSessionCommand.class;
    }

    @Override
    protected GameResult execute(CreateSessionCommand command) {
        return GameResult.success(actionExecutor.executeCreateSession(command));
    }
}
