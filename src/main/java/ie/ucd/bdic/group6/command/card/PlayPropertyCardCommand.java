package ie.ucd.bdic.group6.command.card;

import ie.ucd.bdic.group6.command.CommandPayload;

import java.util.Map;

public final class PlayPropertyCardCommand extends PlayCardCommand {
    public PlayPropertyCardCommand(String sessionId, String playerId, String cardId, String propertyColor) {
        super(payload(sessionId, playerId, cardId, propertyColor));
    }

    private static Map<String, Object> payload(
            String sessionId,
            String playerId,
            String cardId,
            String propertyColor) {
        Map<String, Object> payload = CommandPayload.session(sessionId, playerId);
        payload.put("cardId", cardId);
        payload.put("playMode", "PROPERTY");
        if (propertyColor != null && !propertyColor.isBlank()) {
            payload.put("propertyColor", propertyColor);
        }
        return payload;
    }
}
