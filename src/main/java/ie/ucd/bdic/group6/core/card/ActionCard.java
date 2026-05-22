package ie.ucd.bdic.group6.core.card;

import ie.ucd.bdic.group6.core.property.PropertyColor;

import java.util.EnumSet;
import java.util.Objects;
import java.util.OptionalInt;
import java.util.Set;

/**
 * Action / rent cards. Bank value is the amount printed on the card corner when present.
 */
public final class ActionCard extends Card {

    private final ActionType actionType;
    private final Integer bankValueM;
    private final Set<PropertyColor> rentColorOptions;

    public ActionCard(
            String id,
            String displayName,
            ActionType actionType,
            Integer bankValueM,
            Set<PropertyColor> rentColorOptions) {
        super(id, CardType.ACTION, displayName);
        this.actionType = Objects.requireNonNull(actionType, "actionType");
        this.bankValueM = bankValueM;
        if (bankValueM != null && bankValueM <= 0) {
            throw new IllegalArgumentException("bankValueM must be positive when present");
        }
        this.rentColorOptions = rentColorOptions == null || rentColorOptions.isEmpty()
                ? Set.of()
                : EnumSet.copyOf(rentColorOptions);
        validateRentOptions();
    }

    private void validateRentOptions() {
        boolean needsOptions = switch (actionType) {
            case RENT_BROWN_OR_LIGHT_BLUE,
                    RENT_PINK_OR_ORANGE,
                    RENT_RED_OR_YELLOW,
                    RENT_DARK_BLUE_OR_GREEN,
                    RENT_RAILROAD_OR_UTILITY -> true;
            default -> false;
        };
        if (needsOptions && rentColorOptions.size() < 2) {
            throw new IllegalArgumentException(actionType + " requires two rent color options");
        }
        if (!needsOptions && !rentColorOptions.isEmpty()) {
            throw new IllegalArgumentException(actionType + " must not carry rentColorOptions");
        }
    }

    public ActionType actionType() {
        return actionType;
    }

    /**
     * For dual-color rent cards, the two colors that can be charged using this card.
     */
    public Set<PropertyColor> rentColorOptions() {
        return rentColorOptions.isEmpty() ? Set.of() : EnumSet.copyOf(rentColorOptions);
    }

    @Override
    public OptionalInt bankValueM() {
        return bankValueM == null ? OptionalInt.empty() : OptionalInt.of(bankValueM);
    }
}
