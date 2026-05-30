package ie.ucd.bdic.group6.command;

import java.util.List;
import java.util.Map;

public class ProposeTradeCommand extends AbstractCommand {
    public ProposeTradeCommand(Map<String, Object> payload) {
        super(payload);
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
}
