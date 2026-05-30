package ie.ucd.bdic.group6.command;

import java.util.Map;

public class RejoinSessionCommand extends AbstractCommand {
    public RejoinSessionCommand(Map<String, Object> payload) {
        super(payload);
    }

    @Override
    public CommandType getCommandType() {
        return CommandType.REJOIN_SESSION;
    }

    public String getSessionIdentity() {
        return (String) payload().get("sessionIdentity");
    }
}