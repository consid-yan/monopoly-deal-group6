// JoinSessionHandler.java
package ie.ucd.bdic.group6.facade.handler;

import ie.ucd.bdic.group6.command.JoinSessionCommand;
import ie.ucd.bdic.group6.core.engine.ActionExecutor;
import ie.ucd.bdic.group6.facade.model.GameResult;

public class JoinSessionHandler extends AbstractCommandHandler<JoinSessionCommand> {
    public JoinSessionHandler(ActionExecutor actionExecutor) {
        super(actionExecutor);
    }

    @Override
    public Class<JoinSessionCommand> commandType() {
        return JoinSessionCommand.class;
    }

    @Override
    protected GameResult execute(JoinSessionCommand command) {
        return GameResult.success(actionExecutor.executeJoinSession(command));
    }
}
