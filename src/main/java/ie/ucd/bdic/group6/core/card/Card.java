package ie.ucd.bdic.group6.core.card;

import ie.ucd.bdic.group6.core.card.deck.StandardDeckFactory;
import ie.ucd.bdic.group6.core.rules.CardBankingRule;

import java.util.Objects;

/**
 * Base type for all Monopoly Deal cards. Stable {@link #id()} is assigned by {@link StandardDeckFactory}.
 */
public sealed abstract class Card permits MoneyCard, PropertyCard, PropertyWildCard, ActionCard {

    private final String id;
    private final CardType type;
    private final String displayName;

    protected Card(String id, CardType type, String displayName) {
        this.id = Objects.requireNonNull(id, "id");
        this.type = Objects.requireNonNull(type, "type");
        this.displayName = displayName == null ? "" : displayName;
    }

    public String id() {
        return id;
    }

    public CardType type() {
        return type;
    }

    public String displayName() {
        return displayName;
    }

    /**
     * Value in millions (M) when this card is placed in a bank pile, or empty if it cannot be banked as money.
     * For official banking rules (what may enter a bank pile, paying from bank, etc.), see {@link CardBankingRule}.
     */
    public abstract java.util.OptionalInt bankValueM();

    /**
     * Printed corner value used when a card is paid as an asset.
     */
    public abstract int faceValueM();

    @Override
    public final boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Card that)) {
            return false;
        }
        return id.equals(that.id);
    }

    @Override
    public final int hashCode() {
        return id.hashCode();
    }

    @Override
    public String toString() {
        return type + "[" + id + "]";
    }
}
