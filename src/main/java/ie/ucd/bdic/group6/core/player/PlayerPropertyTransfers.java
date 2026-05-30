package ie.ucd.bdic.group6.core.player;

import ie.ucd.bdic.group6.core.card.Card;
import ie.ucd.bdic.group6.core.card.PropertyWildCard;
import ie.ucd.bdic.group6.core.common.OperationError;
import ie.ucd.bdic.group6.core.common.OperationResult;
import ie.ucd.bdic.group6.core.property.PropertyColor;
import ie.ucd.bdic.group6.core.property.PropertySet;

import java.util.Objects;
import java.util.Optional;

/**
 * Engine-level composition for moving property / wild cards between players' boards (atomic with rollback).
 */
public final class PlayerPropertyTransfers {

    private PlayerPropertyTransfers() {
    }

    public static OperationResult<Void> transferPropertyCardToRow(
            Player sender,
            Player receiver,
            Card card,
            PropertySet receiverRow,
            Optional<PropertyColor> wildChoice) {
        Objects.requireNonNull(sender, "sender");
        Objects.requireNonNull(receiver, "receiver");
        Objects.requireNonNull(card, "card");
        Objects.requireNonNull(receiverRow, "receiverRow");
        if (!receiver.propertyBoard().ownsRow(receiverRow)) {
            return OperationResult.fail(OperationError.ROW_NOT_OWNED_BY_PLAYER, "receiver row not on receiver board");
        }
        Optional<PropertySet> origin = sender.propertyBoard().findRowContaining(card);
        if (origin.isEmpty()) {
            return OperationResult.fail(OperationError.CARD_NOT_ON_PROPERTY_BOARD, "card not on sender board");
        }
        Optional<PropertyColor> priorWild = Optional.empty();
        if (card instanceof PropertyWildCard wc) {
            priorWild = origin.get().wildAssignmentFor(wc.id());
            if (priorWild.isEmpty()) {
                return OperationResult.fail(OperationError.WILD_COLOR_INVALID, "missing wild assignment on sender row");
            }
        }
        if (!receiverRow.canAdd(card, wildChoice)) {
            return OperationResult.fail(
                    OperationError.RECEIVER_ROW_REJECTS_CARD, "receiver row rejects card with given wild choice");
        }
        if (!sender.propertyBoard().removeCardFromRows(card)) {
            return OperationResult.fail(OperationError.CARD_NOT_ON_PROPERTY_BOARD, "remove failed");
        }
        if (!receiverRow.tryAdd(card, wildChoice)) {
            boolean rolledBack = origin.get().tryAdd(card, priorWild);
            if (!rolledBack) {
                return OperationResult.fail(
                        OperationError.ROLLBACK_FAILED,
                        "failed to rollback property transfer for card " + card.id());
            }
            return OperationResult.fail(
                    OperationError.RECEIVER_ROW_REJECTS_CARD, "receiver row refused card after sender removal");
        }
        sender.propertyBoard().compactEmptyRows();
        return OperationResult.ok();
    }

    public static OperationResult<PropertySet> transferPropertyCardToNewRow(
            Player sender,
            Player receiver,
            Card card,
            PropertyColor anchorColor,
            Optional<PropertyColor> wildChoice) {
        Objects.requireNonNull(anchorColor, "anchorColor");
        PropertySet receiverRow = receiver.propertyBoard().startNewRow(anchorColor);
        OperationResult<Void> moved = transferPropertyCardToRow(sender, receiver, card, receiverRow, wildChoice);
        if (!moved.isSuccess()) {
            if (receiverRow.size() == 0) {
                receiver.propertyBoard().removeRow(receiverRow);
                receiver.propertyBoard().compactEmptyRows();
            }
            return OperationResult.fail(
                    moved.errorCode().orElse(OperationError.GENERIC),
                    moved.errorMessage().orElse(""));
        }
        return OperationResult.ok(receiverRow);
    }

    /**
     * Moves an entire property row to the receiver, preserving {@link PropertySet#rowId()} and house/hotel flags.
     */
    public static OperationResult<PropertySet> transferEntireRow(Player sender, Player receiver, PropertySet sourceRow) {
        Objects.requireNonNull(sourceRow, "sourceRow");
        if (!sender.propertyBoard().ownsRow(sourceRow)) {
            return OperationResult.fail(OperationError.ROW_NOT_OWNED_BY_PLAYER, "source row not on sender board");
        }
        PlayerSnapshot.PropertyRowSnapshot snap = PlayerSnapshot.PropertyRowSnapshot.from(sourceRow);
        if (!sender.propertyBoard().removeRow(sourceRow)) {
            return OperationResult.fail(OperationError.CARD_NOT_ON_PROPERTY_BOARD, "could not remove source row");
        }
        PropertySet target = new PropertySet(snap.anchorColor(), snap.rowId());
        for (Card c : snap.cardsInOrder()) {
            Optional<PropertyColor> wild = Optional.empty();
            if (c instanceof PropertyWildCard wc) {
                PropertyColor choice = snap.wildAssignments().get(wc.id());
                if (choice == null) {
                    rollbackEntireRowRestore(sender, receiver, snap, target);
                    return OperationResult.fail(
                            OperationError.WILD_COLOR_INVALID, "missing wild assignment for " + wc.id());
                }
                wild = Optional.of(choice);
            }
            if (!target.tryAdd(c, wild)) {
                rollbackEntireRowRestore(sender, receiver, snap, target);
                return OperationResult.fail(
                        OperationError.RECEIVER_ROW_REJECTS_CARD, "cannot rebuild row on receiver for card " + c.id());
            }
        }
        target.applyImprovementFlagsForSnapshot(snap.house(), snap.hotel());
        receiver.propertyBoard().addRow(target);
        sender.propertyBoard().compactEmptyRows();
        return OperationResult.ok(target);
    }

    private static void rollbackEntireRowRestore(
            Player sender,
            Player receiver,
            PlayerSnapshot.PropertyRowSnapshot snap,
            PropertySet partialTarget) {
        receiver.propertyBoard().removeRow(partialTarget);
        receiver.propertyBoard().compactEmptyRows();
        PropertySet restored = new PropertySet(snap.anchorColor(), snap.rowId());
        for (Card c : snap.cardsInOrder()) {
            Optional<PropertyColor> wild = Optional.empty();
            if (c instanceof PropertyWildCard wc) {
                wild = Optional.of(snap.wildAssignments().get(wc.id()));
            }
            restored.tryAdd(c, wild);
        }
        restored.applyImprovementFlagsForSnapshot(snap.house(), snap.hotel());
        sender.propertyBoard().addRow(restored);
    }
}
