package ie.ucd.bdic.group6.core.card;

import java.util.OptionalInt;

public final class MoneyCard extends Card {

    private final int valueM;

    public MoneyCard(String id, String displayName, int valueM) {
        super(id, CardType.MONEY, displayName);
        if (valueM != 1 && valueM != 2 && valueM != 3 && valueM != 4 && valueM != 5 && valueM != 10) {
            throw new IllegalArgumentException("Unsupported money denomination: " + valueM);
        }
        this.valueM = valueM;
    }

    public int valueM() {
        return valueM;
    }

    @Override
    public OptionalInt bankValueM() {
        return OptionalInt.of(valueM);
    }
}
