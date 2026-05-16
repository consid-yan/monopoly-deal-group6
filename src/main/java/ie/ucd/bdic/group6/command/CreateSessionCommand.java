package ie.ucd.bdic.group6.command;

import java.util.Map;

public class CreateSessionCommand extends AbstractCommand {
    public CreateSessionCommand(Map<String, Object> payload) {
        super(payload);
    }

    @Override
    public CommandType getCommandType() {
        return CommandType.CREATE_SESSION;
    }

    public String getHostPlayerId() {
        return (String) payload().get("hostPlayerId");
    }

    public Integer getMaxPlayers() {
        return (Integer) payload().get("maxPlayers");
    }
}