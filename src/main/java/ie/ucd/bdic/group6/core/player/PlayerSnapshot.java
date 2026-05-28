package ie.ucd.bdic.group6.core.player;

import ie.ucd.bdic.group6.core.card.Card;
import ie.ucd.bdic.group6.core.card.PropertyWildCard;
import ie.ucd.bdic.group6.core.property.PropertyColor;
import ie.ucd.bdic.group6.core.property.PropertySet;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Immutable-ish snapshot of a player's zones for transactional rollback (e.g. Just Say No).
 */
public record PlayerSnapshot(
        String playerId,
        List<Card> hand,
        List<Card> bank,
        List<PropertyRowSnapshot> propertyRows) {

    public PlayerSnapshot {
        playerId = Objects.requireNonNull(playerId, "playerId");
        hand = List.copyOf(hand);
        bank = List.copyOf(bank);
        propertyRows = List.copyOf(propertyRows);
    }

    public static PlayerSnapshot capture(Player player) {
        List<PropertyRowSnapshot> rows = new ArrayList<>();
        for (PropertySet row : player.propertyBoard().rowsView()) {
            rows.add(PropertyRowSnapshot.from(row));
        }
        return new PlayerSnapshot(player.id(), player.hand().copyCards(), player.bank().copyCards(), rows);
    }

    public static void restore(Player player, PlayerSnapshot snap) {
        if (!player.id().equals(snap.playerId())) {
            throw new IllegalArgumentException("snapshot playerId mismatch");
        }
        player.hand().replaceAll(new ArrayList<>(snap.hand()));
        player.bank().replaceAll(new ArrayList<>(snap.bank()));
        player.propertyBoard().clearAllRows();
        for (PropertyRowSnapshot rowSnap : snap.propertyRows()) {
            PropertySet row = new PropertySet(rowSnap.anchorColor(), rowSnap.rowId());
            for (Card c : rowSnap.cardsInOrder()) {
                Optional<PropertyColor> wild = Optional.empty();
                if (c instanceof PropertyWildCard wc) {
                    PropertyColor choice = rowSnap.wildAssignments().get(wc.id());
                    if (choice == null) {
                        throw new IllegalStateException("missing wild assignment for " + wc.id());
                    }
                    wild = Optional.of(choice);
                }
                if (!row.tryAdd(c, wild)) {
                    throw new IllegalStateException("cannot restore row for anchor " + rowSnap.anchorColor());
                }
            }
            row.applyImprovementFlagsForSnapshot(rowSnap.house(), rowSnap.hotel());
            player.propertyBoard().addRow(row);
        }
    }

    public record PropertyRowSnapshot(
            String rowId,
            PropertyColor anchorColor,
            List<Card> cardsInOrder,
            Map<String, PropertyColor> wildAssignments,
            boolean house,
            boolean hotel) {

        public PropertyRowSnapshot {
            rowId = Objects.requireNonNull(rowId, "rowId");
            Objects.requireNonNull(anchorColor, "anchorColor");
            cardsInOrder = List.copyOf(cardsInOrder);
            wildAssignments = Map.copyOf(wildAssignments);
        }

        public static PropertyRowSnapshot from(PropertySet row) {
            return new PropertyRowSnapshot(
                    row.rowId(),
                    row.anchorColor(),
                    new ArrayList<>(row.cardsView()),
                    new HashMap<>(row.wildAssignmentsView()),
                    row.hasHouse(),
                    row.hasHotel());
        }
    }
}
