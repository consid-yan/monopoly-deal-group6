package ie.ucd.bdic.group6.command;

import java.util.Map;

public class RespondTradeCommand extends AbstractCommand {
    public RespondTradeCommand(Map<String, Object> payload) {
        super(payload);
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
}