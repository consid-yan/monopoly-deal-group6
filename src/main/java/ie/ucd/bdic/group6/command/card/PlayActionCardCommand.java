package ie.ucd.bdic.group6.command.card;

import ie.ucd.bdic.group6.command.CommandPayload;

import java.util.Map;

public final class PlayActionCardCommand extends PlayCardCommand {
    private PlayActionCardCommand(Map<String, Object> payload) {
        super(payload);
    }

    public static Builder builder(String sessionId, String playerId, String cardId) {
        return new Builder(sessionId, playerId, cardId);
    }

    public static final class Builder {
        private final Map<String, Object> payload;

        private Builder(String sessionId, String playerId, String cardId) {
            payload = CommandPayload.session(sessionId, playerId);
            payload.put("cardId", cardId);
            payload.put("playMode", "ACTION");
        }

        public Builder targetPlayerId(String targetPlayerId) {
            putText("targetPlayerId", targetPlayerId);
            return this;
        }

        public void targetCardId(String targetCardId) {
            putText("targetCardId", targetCardId);
        }

        public void targetPropertyRowId(String targetPropertyRowId) {
            putText("targetPropertyRowId", targetPropertyRowId);
        }

        public void offeredCardId(String offeredCardId) {
            putText("offeredCardId", offeredCardId);
        }

        public void requestedCardId(String requestedCardId) {
            putText("requestedCardId", requestedCardId);
        }

        public Builder doubleRentCardId(String doubleRentCardId) {
            putText("doubleRentCardId", doubleRentCardId);
            return this;
        }

        public Builder propertyColor(String propertyColor) {
            putText("propertyColor", propertyColor);
            return this;
        }

        public PlayActionCardCommand build() {
            return new PlayActionCardCommand(Map.copyOf(payload));
        }

        private void putText(String key, String value) {
            if (value != null && !value.isBlank()) {
                payload.put(key, value);
            }
        }
    }
}
