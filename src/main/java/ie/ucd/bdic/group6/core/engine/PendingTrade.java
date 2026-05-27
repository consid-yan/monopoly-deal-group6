package ie.ucd.bdic.group6.core.engine;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * A simplified two-player trade proposal awaiting the target player's response.
 */
public final class PendingTrade {

    public enum TradeStatus {
        PENDING,
        ACCEPTED,
        REJECTED,
        CANCELLED
    }

    private final String tradeId;
    private final String proposerPlayerId;
    private final String targetPlayerId;
    private final List<String> offeredCardIds;
    private final List<String> requestedCardIds;
    private final Instant createdAt;
    private TradeStatus status;
    private Instant respondedAt;

    private PendingTrade(Builder builder) {
        this.tradeId = requireText(builder.tradeId, "tradeId");
        this.proposerPlayerId = requireText(builder.proposerPlayerId, "proposerPlayerId");
        this.targetPlayerId = requireText(builder.targetPlayerId, "targetPlayerId");
        if (proposerPlayerId.equals(targetPlayerId)) {
            throw new IllegalArgumentException("cannot trade with oneself");
        }
        this.offeredCardIds = List.copyOf(Objects.requireNonNull(builder.offeredCardIds, "offeredCardIds"));
        this.requestedCardIds = List.copyOf(Objects.requireNonNull(builder.requestedCardIds, "requestedCardIds"));
        if (offeredCardIds.isEmpty() && requestedCardIds.isEmpty()) {
            throw new IllegalArgumentException("trade must contain offered or requested cards");
        }
        this.createdAt = builder.createdAt == null ? Instant.now() : builder.createdAt;
        this.status = TradeStatus.PENDING;
    }

    public static Builder builder() {
        return new Builder();
    }

    public String tradeId() {
        return tradeId;
    }

    public String proposerPlayerId() {
        return proposerPlayerId;
    }

    public String targetPlayerId() {
        return targetPlayerId;
    }

    public List<String> offeredCardIds() {
        return offeredCardIds;
    }

    public List<String> requestedCardIds() {
        return requestedCardIds;
    }

    public TradeStatus status() {
        return status;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant respondedAt() {
        return respondedAt;
    }

    public boolean isPending() {
        return status == TradeStatus.PENDING;
    }

    public boolean involvesPlayer(String playerId) {
        return proposerPlayerId.equals(playerId) || targetPlayerId.equals(playerId);
    }

    public void accept() {
        complete(TradeStatus.ACCEPTED);
    }

    public void reject() {
        complete(TradeStatus.REJECTED);
    }

    public void cancel() {
        complete(TradeStatus.CANCELLED);
    }

    private void complete(TradeStatus nextStatus) {
        if (!isPending()) {
            throw new IllegalStateException("trade is no longer pending");
        }
        status = nextStatus;
        respondedAt = Instant.now();
    }

    private static String requireText(String value, String name) {
        String text = Objects.requireNonNull(value, name).trim();
        if (text.isEmpty()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return text;
    }

    public static final class Builder {
        private String tradeId = UUID.randomUUID().toString();
        private String proposerPlayerId;
        private String targetPlayerId;
        private List<String> offeredCardIds = List.of();
        private List<String> requestedCardIds = List.of();
        private Instant createdAt;

        private Builder() {
        }

        public Builder tradeId(String tradeId) {
            this.tradeId = tradeId;
            return this;
        }

        public Builder proposerPlayerId(String proposerPlayerId) {
            this.proposerPlayerId = proposerPlayerId;
            return this;
        }

        public Builder targetPlayerId(String targetPlayerId) {
            this.targetPlayerId = targetPlayerId;
            return this;
        }

        public Builder offeredCardIds(List<String> offeredCardIds) {
            this.offeredCardIds = offeredCardIds;
            return this;
        }

        public Builder requestedCardIds(List<String> requestedCardIds) {
            this.requestedCardIds = requestedCardIds;
            return this;
        }

        public Builder createdAt(Instant createdAt) {
            this.createdAt = createdAt;
            return this;
        }

        public PendingTrade build() {
            return new PendingTrade(this);
        }
    }
}
