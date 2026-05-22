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

    public String getPlayMode() {
        return (String) payload().get("playMode");
    }

    public String getPropertyColor() {
        return (String) payload().get("propertyColor");
    }

    public String getPropertyRowIndex() {
        // TODO(command-play-card): Replace row indexes with stable row ids before UI/network persist row references.
        Object value = payload().get("propertyRowIndex");
        return value == null ? null : value.toString();
    }
}
