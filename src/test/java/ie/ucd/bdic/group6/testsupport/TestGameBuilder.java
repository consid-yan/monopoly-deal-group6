package ie.ucd.bdic.group6.testsupport;

import ie.ucd.bdic.group6.core.card.Card;
import ie.ucd.bdic.group6.core.player.Player;
import ie.ucd.bdic.group6.core.property.PropertyColor;
import ie.ucd.bdic.group6.core.property.PropertySet;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Fluent builder for assembling {@link Player} instances in tests without going through a full game engine.
 */
public final class TestGameBuilder {

    private TestGameBuilder() {
    }

    public static Player player(String id, String name) {
        return new Player(id, name);
    }

    public static void putInHand(Player p, Card... cards) {
        for (Card c : cards) {
            p.hand().add(c);
        }
    }

    public static void putInBankSkipValidation(Player p, Card... cards) {
        for (Card c : cards) {
            p.bank().forceAddForTestsOrMigration(c);
        }
    }

    public static PropertySet addPropertyRow(Player p, PropertyColor anchor, Card first, Optional<PropertyColor> wild) {
        PropertySet row = new PropertySet(anchor);
        if (!row.tryAdd(first, wild)) {
            throw new IllegalArgumentException("first card cannot start row");
        }
        p.propertyBoard().addRow(row);
        return row;
    }

    public static List<Card> cards(Card... c) {
        return new ArrayList<>(List.of(c));
    }
}
