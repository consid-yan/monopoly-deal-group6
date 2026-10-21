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
public final class PropertyTransferService {

    public static OperationResult<Void> transferPropertyCardToRow(
            Player sender,
            Player receiver,
            Card card,
            PropertySet receiverRow) {
        return transferPropertyCardToRow(sender, receiver, card, receiverRow, null);
    }

    /**
     * [Deep Dive: Atomic Property Transfer — How is a card safely moved between players?]
     * Step 1: Find the card on the sender's board, capture its wild assignment if applicable.
     * Step 2: Validate the receiver's row can accept the card.
     * Step 3: Remove from sender. Step 4: Add to receiver.
     * CRITICAL: If step 4 fails, the card is rolled back to the sender's original row
     * with its original wild assignment. This prevents card loss due to mid-transfer errors.
     * Used by Sly Deal, Forced Deal, Deal Breaker, and property-based debt payments.
     */
    public static OperationResult<Void> transferPropertyCardToRow(
            Player sender,
            Player receiver,
            Card card,
            PropertySet receiverRow,
            PropertyColor wildChoice) {
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
        PropertyColor priorWild = null;
        if (card instanceof PropertyWildCard wc) {
            priorWild = origin.get().wildAssignmentFor(wc.id()).orElse(null);
            if (priorWild == null) {
                return OperationResult.fail(OperationError.WILD_COLOR_INVALID, "missing wild assignment on sender row");
            }
        }
        if (!receiverRow.canAdd(card, wildChoice)) {
            return OperationResult.fail(
                    OperationError.RECEIVER_ROW_REJECTS_CARD, "receiver row rejects card with given wild choice");
        }
        if (!origin.get().remove(card)) {
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
            PropertyColor anchorColor) {
        return transferPropertyCardToNewRow(sender, receiver, card, anchorColor, null);
    }

    public static OperationResult<PropertySet> transferPropertyCardToNewRow(
            Player sender,
            Player receiver,
            Card card,
            PropertyColor anchorColor,
            PropertyColor wildChoice) {
        Objects.requireNonNull(anchorColor, "anchorColor");
        PropertySet receiverRow = receiver.propertyBoard().startNewRow(anchorColor);
        OperationResult<Void> moved = transferPropertyCardToRow(sender, receiver, card, receiverRow, wildChoice);
        if (!moved.isSuccess()) {
            if (receiverRow.size() == 0) {
                receiver.propertyBoard().compactEmptyRows();
            }
            return OperationResult.fail(
                    moved.errorCode().orElse(OperationError.INTERNAL_ERROR),
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
        if (!sender.propertyBoard().removeRow(sourceRow)) {
            return OperationResult.fail(OperationError.CARD_NOT_ON_PROPERTY_BOARD, "could not remove source row");
        }
        receiver.propertyBoard().addRow(sourceRow);
        sender.propertyBoard().compactEmptyRows();
        return OperationResult.ok(sourceRow);
    }
}
