package ie.ucd.bdic.group6.command.card;

import ie.ucd.bdic.group6.command.AbstractCommand;
import ie.ucd.bdic.group6.command.CommandType;

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

    public String getTargetCardId() {
        return (String) payload().get("targetCardId");
    }

    public String getTargetPropertyRowId() {
        return (String) payload().get("targetPropertyRowId");
    }

    public String getOfferedCardId() {
        return (String) payload().get("offeredCardId");
    }

    public String getRequestedCardId() {
        return (String) payload().get("requestedCardId");
    }

    public String getDoubleRentCardId() {
        return (String) payload().get("doubleRentCardId");
    }

    public String getPlayMode() {
        return (String) payload().get("playMode");
    }

    public String getPropertyColor() {
        return (String) payload().get("propertyColor");
    }

    @Deprecated
    public String getPropertyRowIndex() {
        Object legacyIndex = payload().get("propertyRowIndex");
        return legacyIndex == null ? null : legacyIndex.toString();
    }
}
