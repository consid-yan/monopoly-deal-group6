// StartGameHandler.java
package ie.ucd.bdic.group6.facade.handler;

import ie.ucd.bdic.group6.command.StartGameCommand;
import ie.ucd.bdic.group6.core.engine.ActionExecutor;
import ie.ucd.bdic.group6.facade.model.GameResult;

public class StartGameHandler extends AbstractCommandHandler<StartGameCommand> {
    public StartGameHandler(ActionExecutor actionExecutor) {
        super(actionExecutor);
    }

    @Override
    public Class<StartGameCommand> commandType() {
        return StartGameCommand.class;
    }

    @Override
    protected GameResult execute(StartGameCommand command) {
        return GameResult.success(actionExecutor.executeStartGame(command));
    }
}
