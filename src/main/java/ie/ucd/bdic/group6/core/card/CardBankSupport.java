package ie.ucd.bdic.group6.core.card;

/**
 * Helpers for official Monopoly Deal banking rules.
 *
 * <p>Money cards and action cards may be banked as money. Property cards cannot be banked as money.
 * Rainbow (any-color) wild property cards have no bank value and cannot be used as money when paying.
 */
public final class CardBankSupport {

    private CardBankSupport() {
    }

    /**
     * Whether this card may be placed into a bank pile as money (Operation A).
     */
    public static boolean canBankAsMoney(Card card) {
        return switch (card) {
            case MoneyCard money -> true;
            case ActionCard action -> action.bankValueM().isPresent();
            case PropertyWildCard wild -> wild.bankValueM().isPresent();
            case PropertyCard property -> false;
        };
    }

    /**
     * Whether an action card in the bank may still be played for its effect. Officially: never.
     */
    public static boolean canUseBankedCardAsAction(Card card) {
        return false;
    }

    /**
     * Whether a banked card contributes monetary value when paying (must have {@link Card#bankValueM()}).
     */
    public static int bankPaymentValueM(Card card) {
        return card.bankValueM().orElse(0);
    }
}
