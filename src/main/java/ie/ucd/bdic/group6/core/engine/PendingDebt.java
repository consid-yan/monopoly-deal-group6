package ie.ucd.bdic.group6.core.engine;

import ie.ucd.bdic.group6.core.card.ActionType;

import java.util.Objects;
import java.util.UUID;

/**
 * A payment obligation created by an action card or other engine event.
 */
public final class PendingDebt {

    private final String debtId;
    private final String creditorPlayerId;
    private final String debtorPlayerId;
    private final int amountM;
    private final ActionType sourceAction;
    private final String sourceCardId;
    private boolean paid;

    private PendingDebt(Builder builder) {
        this.debtId = requireText(builder.debtId, "debtId");
        this.creditorPlayerId = requireText(builder.creditorPlayerId, "creditorPlayerId");
        this.debtorPlayerId = requireText(builder.debtorPlayerId, "debtorPlayerId");
        if (creditorPlayerId.equals(debtorPlayerId)) {
            throw new IllegalArgumentException("creditor and debtor must be different players");
        }
        if (builder.amountM <= 0) {
            throw new IllegalArgumentException("amountM must be positive");
        }
        this.amountM = builder.amountM;
        this.sourceAction = builder.sourceAction;
        this.sourceCardId = builder.sourceCardId;
    }

    public static Builder builder() {
        return new Builder();
    }

    public String debtId() {
        return debtId;
    }

    public String creditorPlayerId() {
        return creditorPlayerId;
    }

    public String debtorPlayerId() {
        return debtorPlayerId;
    }

    public int amountM() {
        return amountM;
    }

    public ActionType sourceAction() {
        return sourceAction;
    }

    public String sourceCardId() {
        return sourceCardId;
    }

    public boolean isPaid() {
        return paid;
    }

    public boolean isPending() {
        return !paid;
    }

    public void markAsPaid() {
        paid = true;
    }

    private static String requireText(String value, String name) {
        String text = Objects.requireNonNull(value, name).trim();
        if (text.isEmpty()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return text;
    }

    public static final class Builder {
        private String debtId = UUID.randomUUID().toString();
        private String creditorPlayerId;
        private String debtorPlayerId;
        private int amountM;
        private ActionType sourceAction;
        private String sourceCardId;

        private Builder() {
        }

        public Builder debtId(String debtId) {
            this.debtId = debtId;
            return this;
        }

        public Builder creditorPlayerId(String creditorPlayerId) {
            this.creditorPlayerId = creditorPlayerId;
            return this;
        }

        public Builder debtorPlayerId(String debtorPlayerId) {
            this.debtorPlayerId = debtorPlayerId;
            return this;
        }

        public Builder amountM(int amountM) {
            this.amountM = amountM;
            return this;
        }

        public Builder sourceAction(ActionType sourceAction) {
            this.sourceAction = sourceAction;
            return this;
        }

        public Builder sourceCardId(String sourceCardId) {
            this.sourceCardId = sourceCardId;
            return this;
        }

        public PendingDebt build() {
            return new PendingDebt(this);
        }
    }
}
