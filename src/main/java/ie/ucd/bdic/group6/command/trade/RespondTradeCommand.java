package ie.ucd.bdic.group6.command.trade;

import ie.ucd.bdic.group6.command.AbstractCommand;
import ie.ucd.bdic.group6.command.CommandPayload;
import ie.ucd.bdic.group6.command.CommandType;

import java.util.Map;

public class RespondTradeCommand extends AbstractCommand {
    public RespondTradeCommand(Map<String, Object> payload) {
        super(payload);
    }

    public RespondTradeCommand(String sessionId, String playerId, String tradeId, boolean accepted) {
        this(payload(sessionId, playerId, tradeId, accepted));
    }

    @Override
    public CommandType getCommandType() {
        return CommandType.RESPOND_TRADE;
    }

    public String getTradeId() {
        return (String) payload().get("tradeId");
    }

    public boolean isAccepted() {
        return Boolean.TRUE.equals(payload().get("accepted"));
    }

    private static Map<String, Object> payload(String sessionId, String playerId, String tradeId, boolean accepted) {
        Map<String, Object> payload = CommandPayload.session(sessionId, playerId);
        payload.put("tradeId", tradeId);
        payload.put("accepted", accepted);
        return payload;
    }
}
