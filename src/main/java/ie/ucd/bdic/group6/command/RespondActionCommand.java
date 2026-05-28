package ie.ucd.bdic.group6.command;

import java.util.Map;

public class RespondActionCommand extends AbstractCommand {
    public RespondActionCommand(Map<String, Object> payload) {
        super(payload);
    }

    @Override
    public CommandType getCommandType() {
        return CommandType.RESPOND_ACTION;
    }

    public String getActionId() {
        return (String) payload().get("actionId");
    }

    public boolean isAccepted() {
        return Boolean.TRUE.equals(payload().get("accepted"));
    }

    public String getJustSayNoCardId() {
        return (String) payload().get("justSayNoCardId");
    }
}
