package ie.ucd.bdic.group6.facade.handler;

import ie.ucd.bdic.group6.command.PlayerDisconnectedCommand;
import ie.ucd.bdic.group6.core.engine.ActionExecutor;
import ie.ucd.bdic.group6.facade.model.GameResult;

public class PlayerDisconnectedHandler extends AbstractCommandHandler<PlayerDisconnectedCommand> {
    public PlayerDisconnectedHandler(ActionExecutor actionExecutor) {
        super(actionExecutor);
    }

    @Override
    public Class<PlayerDisconnectedCommand> commandType() {
        return PlayerDisconnectedCommand.class;
    }

    @Override
    protected GameResult execute(PlayerDisconnectedCommand command) {
        return GameResult.success(actionExecutor.executePlayerDisconnected(command));
    }
}
