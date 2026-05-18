package ie.ucd.bdic.group6.core.card;

import ie.ucd.bdic.group6.core.property.PropertyColor;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Builds the standard 106-card Monopoly Deal play deck (110 physical cards minus 4 quick-rule reference cards).
 * Composition is driven by {@link DeckDefinition}.
 */
public final class StandardDeckFactory {

    private int moneySeq;
    private int propertySeq;
    private int wildSeq;
    private int actionSeq;

    private StandardDeckFactory() {
    }

    public static List<Card> createUnshuffledDeck() {
        return new StandardDeckFactory().build();
    }

    private List<Card> build() {
        List<Card> cards = new ArrayList<>(DeckDefinition.TOTAL_PLAY_CARDS);
        addMoney(cards);
        addProperties(cards);
        addWilds(cards);
        addNonRentActions(cards);
        addRentCards(cards);
        if (cards.size() != DeckDefinition.TOTAL_PLAY_CARDS) {
            throw new IllegalStateException(
                    "Deck size must be " + DeckDefinition.TOTAL_PLAY_CARDS + " but was " + cards.size());
        }
        return cards;
    }

    private void addMoney(List<Card> cards) {
        repeat(cards, DeckDefinition.MONEY_1M, () -> new MoneyCard(id("money", ++moneySeq), "1M", 1));
        repeat(cards, DeckDefinition.MONEY_2M, () -> new MoneyCard(id("money", ++moneySeq), "2M", 2));
        repeat(cards, DeckDefinition.MONEY_3M, () -> new MoneyCard(id("money", ++moneySeq), "3M", 3));
        repeat(cards, DeckDefinition.MONEY_4M, () -> new MoneyCard(id("money", ++moneySeq), "4M", 4));
        repeat(cards, DeckDefinition.MONEY_5M, () -> new MoneyCard(id("money", ++moneySeq), "5M", 5));
        repeat(cards, DeckDefinition.MONEY_10M, () -> new MoneyCard(id("money", ++moneySeq), "10M", 10));
    }

    private void addProperties(List<Card> cards) {
        repeatProperty(cards, PropertyColor.BROWN, "Brown", DeckDefinition.PROPERTY_BROWN);
        repeatProperty(cards, PropertyColor.LIGHT_BLUE, "Light Blue", DeckDefinition.PROPERTY_LIGHT_BLUE);
        repeatProperty(cards, PropertyColor.PINK, "Pink", DeckDefinition.PROPERTY_PINK);
        repeatProperty(cards, PropertyColor.ORANGE, "Orange", DeckDefinition.PROPERTY_ORANGE);
        repeatProperty(cards, PropertyColor.RED, "Red", DeckDefinition.PROPERTY_RED);
        repeatProperty(cards, PropertyColor.YELLOW, "Yellow", DeckDefinition.PROPERTY_YELLOW);
        repeatProperty(cards, PropertyColor.GREEN, "Green", DeckDefinition.PROPERTY_GREEN);
        repeatProperty(cards, PropertyColor.DARK_BLUE, "Dark Blue", DeckDefinition.PROPERTY_DARK_BLUE);
        repeatProperty(cards, PropertyColor.RAILROAD, "Railroad", DeckDefinition.PROPERTY_RAILROAD);
        repeatProperty(cards, PropertyColor.UTILITY, "Utility", DeckDefinition.PROPERTY_UTILITY);
    }

    private void repeatProperty(List<Card> cards, PropertyColor color, String label, int times) {
        int need = PropertyColor.fullSetSize(color);
        for (int i = 0; i < times; i++) {
            cards.add(new PropertyCard(
                    id("property", ++propertySeq),
                    label + " " + (i + 1),
                    color,
                    need));
        }
    }

    private void addWilds(List<Card> cards) {
        cards.add(wild("Brown / Light Blue", EnumSet.of(PropertyColor.BROWN, PropertyColor.LIGHT_BLUE), 1));
        cards.add(wild("Light Blue / Railroad", EnumSet.of(PropertyColor.LIGHT_BLUE, PropertyColor.RAILROAD), 2));
        cards.add(wild("Railroad / Utility", EnumSet.of(PropertyColor.RAILROAD, PropertyColor.UTILITY), 2));
        cards.add(wild("Rainbow", EnumSet.allOf(PropertyColor.class), null));
        cards.add(wild("Rainbow", EnumSet.allOf(PropertyColor.class), null));
        cards.add(wild("Pink / Orange", EnumSet.of(PropertyColor.PINK, PropertyColor.ORANGE), 2));
        cards.add(wild("Pink / Orange", EnumSet.of(PropertyColor.PINK, PropertyColor.ORANGE), 2));
        cards.add(wild("Red / Yellow", EnumSet.of(PropertyColor.RED, PropertyColor.YELLOW), 2));
        cards.add(wild("Red / Yellow", EnumSet.of(PropertyColor.RED, PropertyColor.YELLOW), 2));
        cards.add(wild("Dark Blue / Green", EnumSet.of(PropertyColor.DARK_BLUE, PropertyColor.GREEN), 4));
        cards.add(wild("Green / Railroad", EnumSet.of(PropertyColor.GREEN, PropertyColor.RAILROAD), 4));
    }

    private PropertyWildCard wild(String name, Set<PropertyColor> allowed, Integer bank) {
        return new PropertyWildCard(id("wild", ++wildSeq), name, allowed, bank);
    }

    private void addNonRentActions(List<Card> cards) {
        repeat(cards, DeckDefinition.ACTION_DEAL_BREAKER, () -> action("Deal Breaker", ActionType.DEAL_BREAKER, 5, null));
        repeat(cards, DeckDefinition.ACTION_FORCED_DEAL, () -> action("Forced Deal", ActionType.FORCED_DEAL, 3, null));
        repeat(cards, DeckDefinition.ACTION_SLY_DEAL, () -> action("Sly Deal", ActionType.SLY_DEAL, 3, null));
        repeat(cards, DeckDefinition.ACTION_JUST_SAY_NO, () -> action("Just Say No", ActionType.JUST_SAY_NO, 4, null));
        repeat(cards, DeckDefinition.ACTION_DEBT_COLLECTOR, () -> action("Debt Collector", ActionType.DEBT_COLLECTOR, 5, null));
        repeat(cards, DeckDefinition.ACTION_ITS_MY_BIRTHDAY, () -> action("It's My Birthday", ActionType.ITS_MY_BIRTHDAY, 2, null));
        repeat(cards, DeckDefinition.ACTION_DOUBLE_THE_RENT, () -> action("Double The Rent", ActionType.DOUBLE_THE_RENT, 1, null));
        repeat(cards, DeckDefinition.ACTION_HOUSE, () -> action("House", ActionType.HOUSE, 3, null));
        repeat(cards, DeckDefinition.ACTION_HOTEL, () -> action("Hotel", ActionType.HOTEL, 4, null));
        repeat(cards, DeckDefinition.ACTION_PASS_GO, () -> action("Pass Go", ActionType.PASS_GO, 1, null));
    }

    private void addRentCards(List<Card> cards) {
        repeat(cards, DeckDefinition.RENT_DUAL_EACH, () -> rentDual("Rent", ActionType.RENT_BROWN_OR_LIGHT_BLUE, 1, PropertyColor.BROWN, PropertyColor.LIGHT_BLUE));
        repeat(cards, DeckDefinition.RENT_DUAL_EACH, () -> rentDual("Rent", ActionType.RENT_PINK_OR_ORANGE, 1, PropertyColor.PINK, PropertyColor.ORANGE));
        repeat(cards, DeckDefinition.RENT_DUAL_EACH, () -> rentDual("Rent", ActionType.RENT_RED_OR_YELLOW, 1, PropertyColor.RED, PropertyColor.YELLOW));
        repeat(cards, DeckDefinition.RENT_DUAL_EACH, () -> rentDual("Rent", ActionType.RENT_DARK_BLUE_OR_GREEN, 1, PropertyColor.DARK_BLUE, PropertyColor.GREEN));
        repeat(cards, DeckDefinition.RENT_DUAL_EACH, () -> rentDual("Rent", ActionType.RENT_RAILROAD_OR_UTILITY, 1, PropertyColor.RAILROAD, PropertyColor.UTILITY));
        repeat(cards, DeckDefinition.RENT_WILD, () -> action("Wild Rent", ActionType.WILD_RENT, 3, null));
    }

    private ActionCard rentDual(String name, ActionType type, int bank, PropertyColor a, PropertyColor b) {
        return new ActionCard(id("action", ++actionSeq), name + " (" + a + "/" + b + ")", type, bank, EnumSet.of(a, b));
    }

    private ActionCard action(String name, ActionType type, int bank, Set<PropertyColor> rentOpts) {
        return new ActionCard(id("action", ++actionSeq), name, type, bank, rentOpts);
    }

    private static String id(String prefix, int n) {
        return prefix + "-" + n;
    }

    private static void repeat(List<Card> cards, int times, java.util.function.Supplier<Card> supplier) {
        for (int i = 0; i < times; i++) {
            cards.add(supplier.get());
        }
    }
}
