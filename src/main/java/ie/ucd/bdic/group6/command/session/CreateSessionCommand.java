package ie.ucd.bdic.group6.command.session;

import ie.ucd.bdic.group6.command.AbstractCommand;
import ie.ucd.bdic.group6.command.CommandType;

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
}
