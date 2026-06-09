package ie.ucd.bdic.group6.facade.handler;

import ie.ucd.bdic.group6.command.connection.PlayerDisconnectedCommand;
import ie.ucd.bdic.group6.core.engine.ActionExecutor;
import ie.ucd.bdic.group6.facade.model.CommandResult;

public class PlayerDisconnectedHandler extends AbstractCommandHandler<PlayerDisconnectedCommand> {
    public PlayerDisconnectedHandler(ActionExecutor actionExecutor) {
        super(actionExecutor);
    }

    @Override
    public Class<PlayerDisconnectedCommand> commandType() {
        return PlayerDisconnectedCommand.class;
    }

    @Override
    protected CommandResult execute(PlayerDisconnectedCommand command) {
        return CommandResult.success(actionExecutor.executePlayerDisconnected(command));
    }
}
