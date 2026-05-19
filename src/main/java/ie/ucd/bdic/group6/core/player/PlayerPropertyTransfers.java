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
 * Engine-level composition for moving a property / wild between players' boards (atomic with rollback).
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
        return OperationResult.ok();
    }
}
