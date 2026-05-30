package ie.ucd.bdic.group6.command;

import java.util.Map;

public class JoinSessionCommand extends AbstractCommand {
    public JoinSessionCommand(Map<String, Object> payload) {
        super(payload);
    }

    @Override
    public CommandType getCommandType() {
        return CommandType.JOIN_SESSION;
    }

    public String getSessionCode() {
        return (String) payload().get("sessionCode");
    }
}