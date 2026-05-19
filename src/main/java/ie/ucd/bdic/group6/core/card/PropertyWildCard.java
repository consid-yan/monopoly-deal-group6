package ie.ucd.bdic.group6.core.card;

import ie.ucd.bdic.group6.core.property.PropertyColor;

import java.util.EnumSet;
import java.util.Objects;
import java.util.OptionalInt;
import java.util.Set;

/**
 * Property wild cards: can stand for one of the allowed colors when placed in a property row.
 * {@link #bankValueM()} is empty for rainbow (any-color) wilds; dual-color wilds use the printed bank value.
 */
public final class PropertyWildCard extends Card {

    private final Set<PropertyColor> allowedColors;
    private final Integer bankValueM;

    public PropertyWildCard(String id, String displayName, Set<PropertyColor> allowedColors, Integer bankValueM) {
        super(id, CardType.PROPERTY_WILD, displayName);
        Objects.requireNonNull(allowedColors, "allowedColors");
        if (allowedColors.isEmpty()) {
            throw new IllegalArgumentException("allowedColors must not be empty");
        }
        this.allowedColors = EnumSet.copyOf(allowedColors);
        this.bankValueM = bankValueM;
        if (bankValueM != null && bankValueM <= 0) {
            throw new IllegalArgumentException("bankValueM must be positive when present");
        }
    }

    public Set<PropertyColor> allowedColors() {
        return EnumSet.copyOf(allowedColors);
    }

    public boolean allows(PropertyColor color) {
        return allowedColors.contains(color);
    }

    @Override
    public OptionalInt bankValueM() {
        return bankValueM == null ? OptionalInt.empty() : OptionalInt.of(bankValueM);
    }
}
