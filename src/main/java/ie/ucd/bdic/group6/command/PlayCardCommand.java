// PlayCardCommand.java
package ie.ucd.bdic.group6.command;

import java.util.Map;

public class PlayCardCommand extends AbstractCommand {
    public PlayCardCommand(Map<String, Object> payload) {
        super(payload);
    }

    @Override
    public CommandType getCommandType() {
        return CommandType.PLAY_CARD;
    }

    public String getCardId() {
        return (String) payload().get("cardId");
    }

    public String getTargetPlayerId() {
        return (String) payload().get("targetPlayerId");
    }
}