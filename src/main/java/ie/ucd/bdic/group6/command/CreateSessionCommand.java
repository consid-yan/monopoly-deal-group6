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
        Object raw = payload().get("maxPlayers");
        if (raw == null) {
            return null;
        }
        if (raw instanceof Number n) {
            return n.intValue();
        }
        try {
            return Integer.parseInt(raw.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}