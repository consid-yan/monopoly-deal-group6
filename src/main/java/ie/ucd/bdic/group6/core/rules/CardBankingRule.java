package ie.ucd.bdic.group6.core.rules;

import ie.ucd.bdic.group6.core.card.Card;

/**
 * Helpers for official Monopoly Deal banking rules.
 *
 * <p>Money cards and action cards may be banked as money. Property cards cannot be banked as money.
 * Rainbow (any-color) wild property cards have no bank value and cannot be used as money when paying.
 */
public final class CardBankingRule {

    /**
     * Whether this card may be placed into a bank pile as money (Operation A).
     */
    public static boolean canBankAsMoney(Card card) {
        return card.bankValueM().isPresent();
    }

    /**
     * Whether a banked card contributes monetary value when paying (must have {@link Card#bankValueM()}).
     */
    public static int bankPaymentValueM(Card card) {
        return card.bankValueM().orElse(0);
    }
}
