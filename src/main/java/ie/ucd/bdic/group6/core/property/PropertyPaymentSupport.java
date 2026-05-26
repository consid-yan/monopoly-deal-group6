package ie.ucd.bdic.group6.core.property;

import ie.ucd.bdic.group6.core.card.Card;
import ie.ucd.bdic.group6.core.card.PropertyCard;
import ie.ucd.bdic.group6.core.card.PropertyWildCard;
import ie.ucd.bdic.group6.core.common.OperationError;
import ie.ucd.bdic.group6.core.common.OperationResult;

import java.util.Objects;

/**
 * Monopoly Deal property-as-payment rules: value credited toward a debt is the rent of the row
 * containing the card at payment time (before the card leaves the row).
 */
public final class PropertyPaymentSupport {

    private PropertyPaymentSupport() {
    }

    public static boolean canUseAsPropertyPayment(Card card) {
        return card instanceof PropertyCard || card instanceof PropertyWildCard;
    }

    public static boolean canUseAsPropertyPayment(Card card, PropertySet row) {
        Objects.requireNonNull(row, "row");
        return canUseAsPropertyPayment(card) && row.contains(card);
    }

    public static OperationResult<Integer> paymentValueM(PropertySet row, Card card) {
        Objects.requireNonNull(row, "row");
        Objects.requireNonNull(card, "card");
        if (!canUseAsPropertyPayment(card)) {
            return OperationResult.fail(
                    OperationError.PROPERTY_CANNOT_PAY_DEBT,
                    "only property or property-wild cards can pay as property: " + card.id());
        }
        if (!row.contains(card)) {
            return OperationResult.fail(
                    OperationError.CARD_NOT_ON_PROPERTY_BOARD, "card is not on the given property row: " + card.id());
        }
        return OperationResult.ok(row.rentValue());
    }
}
