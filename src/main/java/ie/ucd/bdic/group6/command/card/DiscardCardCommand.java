package ie.ucd.bdic.group6.command.card;

import ie.ucd.bdic.group6.command.AbstractCommand;
import ie.ucd.bdic.group6.command.CommandPayload;
import ie.ucd.bdic.group6.command.CommandType;

import java.util.List;
import java.util.Map;

public class DiscardCardCommand extends AbstractCommand {
    public DiscardCardCommand(Map<String, Object> payload) {
        super(payload);
    }

    public DiscardCardCommand(String sessionId, String playerId, List<String> discardedCardIds) {
        this(payload(sessionId, playerId, discardedCardIds));
    }

    @Override
    public CommandType getCommandType() {
        return CommandType.DISCARD_CARDS;
    }

    @SuppressWarnings("unchecked")
    public List<String> getDiscardedCardIds() {
        return (List<String>) payload().get("discardedCardIds");
    }

    private static Map<String, Object> payload(String sessionId, String playerId, List<String> discardedCardIds) {
        Map<String, Object> payload = CommandPayload.session(sessionId, playerId);
        payload.put("discardedCardIds", discardedCardIds);
        return payload;
    }
}
