package ie.ucd.bdic.group6.command;

import java.util.List;
import java.util.Map;

public class DiscardCardsCommand extends AbstractCommand {
    public DiscardCardsCommand(Map<String, Object> payload) {
        super(payload);
    }

    @Override
    public CommandType getCommandType() {
        return CommandType.DISCARD_CARDS;
    }

    @SuppressWarnings("unchecked")
    public List<String> getDiscardedCardIds() {
        return (List<String>) payload().get("discardedCardIds");
    }
}