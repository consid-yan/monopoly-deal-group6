package ie.ucd.bdic.group6.command.card;

import ie.ucd.bdic.group6.command.CommandPayload;

import java.util.Map;

public final class BankCardCommand extends PlayCardCommand {
    public BankCardCommand(String sessionId, String playerId, String cardId) {
        super(payload(sessionId, playerId, cardId));
    }

    private static Map<String, Object> payload(String sessionId, String playerId, String cardId) {
        Map<String, Object> payload = CommandPayload.session(sessionId, playerId);
        payload.put("cardId", cardId);
        payload.put("playMode", "BANK");
        return payload;
    }
}
