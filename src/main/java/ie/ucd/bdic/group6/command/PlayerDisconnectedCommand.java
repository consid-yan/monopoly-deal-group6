package ie.ucd.bdic.group6.command;

import java.util.Map;

public class PlayerDisconnectedCommand extends AbstractCommand {
    public PlayerDisconnectedCommand(Map<String, Object> payload) {
        super(payload);
    }

    @Override
    public CommandType getCommandType() {
        return CommandType.PLAYER_DISCONNECTED;
    }
}
