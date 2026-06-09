package ie.ucd.bdic.group6.command.action;

import ie.ucd.bdic.group6.command.AbstractCommand;
import ie.ucd.bdic.group6.command.CommandPayload;
import ie.ucd.bdic.group6.command.CommandType;

import java.util.Map;

public class RespondActionCommand extends AbstractCommand {
    public RespondActionCommand(Map<String, Object> payload) {
        super(payload);
    }

    public RespondActionCommand(
            String sessionId,
            String playerId,
            String actionId,
            boolean accepted,
            String justSayNoCardId) {
        this(payload(sessionId, playerId, actionId, accepted, justSayNoCardId));
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

    private static Map<String, Object> payload(
            String sessionId,
            String playerId,
            String actionId,
            boolean accepted,
            String justSayNoCardId) {
        Map<String, Object> payload = CommandPayload.session(sessionId, playerId);
        payload.put("actionId", actionId);
        payload.put("accepted", accepted);
        if (justSayNoCardId != null && !justSayNoCardId.isBlank()) {
            payload.put("justSayNoCardId", justSayNoCardId);
        }
        return payload;
    }
}
