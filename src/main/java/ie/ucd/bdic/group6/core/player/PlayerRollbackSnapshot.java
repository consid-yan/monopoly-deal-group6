package ie.ucd.bdic.group6.core.player;

import ie.ucd.bdic.group6.core.card.Card;
import ie.ucd.bdic.group6.core.card.PropertyWildCard;
import ie.ucd.bdic.group6.core.property.PropertyColor;
import ie.ucd.bdic.group6.core.property.PropertySet;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Immutable-ish snapshot of a player's zones for transactional rollback (e.g. Just Say No).
 */
public record PlayerRollbackSnapshot(
        String playerId,
        List<Card> hand,
        List<Card> bank,
        List<PropertyRowSnapshot> propertyRows) {

    public PlayerRollbackSnapshot {
        playerId = Objects.requireNonNull(playerId, "playerId");
        hand = List.copyOf(hand);
        bank = List.copyOf(bank);
        propertyRows = List.copyOf(propertyRows);
    }

    /**
     * [Deep Dive: Transactional Rollback — How does the engine undo failed operations?]
     * Takes a complete snapshot of the player's Hand, Bank, and every PropertyRow
     * (including wild card color assignments and house/hotel flags).
     * Used before multi-step operations like trades and debt payments:
     *   snapshot = player.snapshot() → try operation → on failure: player.restore(snapshot)
     * This is the "all or nothing" guarantee: either the entire operation succeeds,
     * or the player's state is restored exactly as it was before the attempt.
     * Also used by Just Say No rollbacks in PropertyTransferService.
     */
    public static PlayerRollbackSnapshot capture(Player player) {
        List<PropertyRowSnapshot> rows = new ArrayList<>();
        for (PropertySet row : player.propertyBoard().rowsView()) {
            rows.add(PropertyRowSnapshot.from(row));
        }
        return new PlayerRollbackSnapshot(player.id(), player.handView(), player.bankView(), rows);
    }

    public static void restore(Player player, PlayerRollbackSnapshot snap) {
        if (!player.id().equals(snap.playerId())) {
            throw new IllegalArgumentException("snapshot playerId mismatch");
        }
        player.hand().replaceAll(snap.hand());
        player.bank().replaceAll(snap.bank());
        player.propertyBoard().clearAllRows();
        for (PropertyRowSnapshot rowSnap : snap.propertyRows()) {
            PropertySet row = new PropertySet(rowSnap.anchorColor(), rowSnap.rowId());
            for (Card c : rowSnap.cardsInOrder()) {
                PropertyColor wild = null;
                if (c instanceof PropertyWildCard wc) {
                    PropertyColor choice = rowSnap.wildAssignments().get(wc.id());
                    if (choice == null) {
                        throw new IllegalStateException("missing wild assignment for " + wc.id());
                    }
                    wild = choice;
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
                    row.cardsView(),
                    row.wildAssignmentsView(),
                    row.hasHouse(),
                    row.hasHotel());
        }
    }
}
