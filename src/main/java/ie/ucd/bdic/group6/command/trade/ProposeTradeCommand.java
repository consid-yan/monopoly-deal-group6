package ie.ucd.bdic.group6.command.trade;

import ie.ucd.bdic.group6.command.AbstractCommand;
import ie.ucd.bdic.group6.command.CommandPayload;
import ie.ucd.bdic.group6.command.CommandType;

import java.util.List;
import java.util.Map;

public class ProposeTradeCommand extends AbstractCommand {
    public ProposeTradeCommand(Map<String, Object> payload) {
        super(payload);
    }

    public ProposeTradeCommand(
            String sessionId,
            String playerId,
            String targetPlayerId,
            List<String> offeredCardIds,
            List<String> requestedCardIds) {
        this(payload(sessionId, playerId, targetPlayerId, offeredCardIds, requestedCardIds));
    }

    @Override
    public CommandType getCommandType() {
        return CommandType.PROPOSE_TRADE;
    }

    public String getTargetPlayerId() {
        return (String) payload().get("targetPlayerId");
    }

    @SuppressWarnings("unchecked")
    public List<String> getOfferedCardIds() {
        return (List<String>) payload().get("offeredCardIds");
    }

    @SuppressWarnings("unchecked")
    public List<String> getRequestedCardIds() {
        return (List<String>) payload().get("requestedCardIds");
    }

    private static Map<String, Object> payload(
            String sessionId,
            String playerId,
            String targetPlayerId,
            List<String> offeredCardIds,
            List<String> requestedCardIds) {
        Map<String, Object> payload = CommandPayload.session(sessionId, playerId);
        payload.put("targetPlayerId", targetPlayerId);
        payload.put("offeredCardIds", offeredCardIds);
        payload.put("requestedCardIds", requestedCardIds);
        return payload;
    }
}
