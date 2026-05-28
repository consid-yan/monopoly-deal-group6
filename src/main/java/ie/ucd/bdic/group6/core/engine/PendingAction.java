package ie.ucd.bdic.group6.core.engine;

import ie.ucd.bdic.group6.core.card.ActionType;
import ie.ucd.bdic.group6.core.property.PropertyColor;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * A played action card waiting for target players to accept it or cancel their part with Just Say No.
 */
public final class PendingAction {

    public enum Response {
        PENDING,
        ACCEPTED,
        REJECTED
    }

    private final String actionId;
    private final String actorPlayerId;
    private final ActionType actionType;
    private final String actionCardId;
    private final String doubleRentCardId;
    private final PropertyColor propertyColor;
    private final int amountM;
    private final String targetCardId;
    private final String targetPropertyRowId;
    private final String offeredCardId;
    private final String requestedCardId;
    private final Instant createdAt;
    private final Map<String, Response> responsesByTarget = new LinkedHashMap<>();

    private PendingAction(Builder builder) {
        this.actionId = requireText(builder.actionId, "actionId");
        this.actorPlayerId = requireText(builder.actorPlayerId, "actorPlayerId");
        this.actionType = Objects.requireNonNull(builder.actionType, "actionType");
        this.actionCardId = requireText(builder.actionCardId, "actionCardId");
        this.doubleRentCardId = blankToNull(builder.doubleRentCardId);
        this.propertyColor = builder.propertyColor;
        this.amountM = builder.amountM;
        this.targetCardId = blankToNull(builder.targetCardId);
        this.targetPropertyRowId = blankToNull(builder.targetPropertyRowId);
        this.offeredCardId = blankToNull(builder.offeredCardId);
        this.requestedCardId = blankToNull(builder.requestedCardId);
        this.createdAt = builder.createdAt == null ? Instant.now() : builder.createdAt;
        List<String> targetIds = List.copyOf(Objects.requireNonNull(builder.targetPlayerIds, "targetPlayerIds"));
        if (targetIds.isEmpty()) {
            throw new IllegalArgumentException("targetPlayerIds must not be empty");
        }
        for (String targetId : targetIds) {
            responsesByTarget.put(requireText(targetId, "targetPlayerId"), Response.PENDING);
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    public String actionId() {
        return actionId;
    }

    public String actorPlayerId() {
        return actorPlayerId;
    }

    public ActionType actionType() {
        return actionType;
    }

    public String actionCardId() {
        return actionCardId;
    }

    public Optional<String> doubleRentCardId() {
        return Optional.ofNullable(doubleRentCardId);
    }

    public Optional<PropertyColor> propertyColor() {
        return Optional.ofNullable(propertyColor);
    }

    public int amountM() {
        return amountM;
    }

    public Optional<String> targetCardId() {
        return Optional.ofNullable(targetCardId);
    }

    public Optional<String> targetPropertyRowId() {
        return Optional.ofNullable(targetPropertyRowId);
    }

    public Optional<String> offeredCardId() {
        return Optional.ofNullable(offeredCardId);
    }

    public Optional<String> requestedCardId() {
        return Optional.ofNullable(requestedCardId);
    }

    public Instant createdAt() {
        return createdAt;
    }

    public List<String> targetPlayerIds() {
        return List.copyOf(responsesByTarget.keySet());
    }

    public Map<String, Response> responsesView() {
        return Map.copyOf(responsesByTarget);
    }

    public boolean awaitsResponseFrom(String playerId) {
        return responsesByTarget.get(playerId) == Response.PENDING;
    }

    public void accept(String playerId) {
        setResponse(playerId, Response.ACCEPTED);
    }

    public void reject(String playerId) {
        setResponse(playerId, Response.REJECTED);
    }

    public boolean isComplete() {
        return responsesByTarget.values().stream().noneMatch(response -> response == Response.PENDING);
    }

    public List<String> acceptedTargetIds() {
        return responsesByTarget.entrySet().stream()
                .filter(entry -> entry.getValue() == Response.ACCEPTED)
                .map(Map.Entry::getKey)
                .toList();
    }

    private void setResponse(String playerId, Response response) {
        if (!responsesByTarget.containsKey(playerId)) {
            throw new IllegalArgumentException("player is not a target for this action: " + playerId);
        }
        if (responsesByTarget.get(playerId) != Response.PENDING) {
            throw new IllegalStateException("player already responded to this action: " + playerId);
        }
        responsesByTarget.put(playerId, response);
    }

    private static String requireText(String value, String name) {
        String text = Objects.requireNonNull(value, name).trim();
        if (text.isEmpty()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return text;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    public static final class Builder {
        private String actionId = UUID.randomUUID().toString();
        private String actorPlayerId;
        private ActionType actionType;
        private String actionCardId;
        private String doubleRentCardId;
        private PropertyColor propertyColor;
        private int amountM;
        private String targetCardId;
        private String targetPropertyRowId;
        private String offeredCardId;
        private String requestedCardId;
        private List<String> targetPlayerIds = List.of();
        private Instant createdAt;

        private Builder() {
        }

        public Builder actorPlayerId(String actorPlayerId) {
            this.actorPlayerId = actorPlayerId;
            return this;
        }

        public Builder actionType(ActionType actionType) {
            this.actionType = actionType;
            return this;
        }

        public Builder actionCardId(String actionCardId) {
            this.actionCardId = actionCardId;
            return this;
        }

        public Builder doubleRentCardId(String doubleRentCardId) {
            this.doubleRentCardId = doubleRentCardId;
            return this;
        }

        public Builder propertyColor(PropertyColor propertyColor) {
            this.propertyColor = propertyColor;
            return this;
        }

        public Builder amountM(int amountM) {
            this.amountM = amountM;
            return this;
        }

        public Builder targetCardId(String targetCardId) {
            this.targetCardId = targetCardId;
            return this;
        }

        public Builder targetPropertyRowId(String targetPropertyRowId) {
            this.targetPropertyRowId = targetPropertyRowId;
            return this;
        }

        public Builder offeredCardId(String offeredCardId) {
            this.offeredCardId = offeredCardId;
            return this;
        }

        public Builder requestedCardId(String requestedCardId) {
            this.requestedCardId = requestedCardId;
            return this;
        }

        public Builder targetPlayerIds(List<String> targetPlayerIds) {
            this.targetPlayerIds = targetPlayerIds;
            return this;
        }

        public Builder createdAt(Instant createdAt) {
            this.createdAt = createdAt;
            return this;
        }

        public PendingAction build() {
            return new PendingAction(this);
        }
    }
}
