package ie.ucd.bdic.group6.core.card;

import ie.ucd.bdic.group6.core.property.PropertyColor;

import java.util.OptionalInt;

public final class PropertyCard extends Card {

    private final PropertyColor color;
    private final int requiredToComplete;

    public PropertyCard(String id, String displayName, PropertyColor color, int requiredToComplete) {
        super(id, CardType.PROPERTY, displayName);
        this.color = java.util.Objects.requireNonNull(color, "color");
        int expected = PropertyColor.fullSetSize(color);
        if (requiredToComplete != expected) {
            throw new IllegalArgumentException(
                    "requiredToComplete " + requiredToComplete + " must match fullSetSize(" + color + ")=" + expected);
        }
        this.requiredToComplete = requiredToComplete;
    }

    public PropertyColor color() {
        return color;
    }

    public int requiredToComplete() {
        return requiredToComplete;
    }

    /**
     * Property cards are never banked as money in Monopoly Deal (only used as properties or payment as properties).
     */
    @Override
    public OptionalInt bankValueM() {
        return OptionalInt.empty();
    }
}
