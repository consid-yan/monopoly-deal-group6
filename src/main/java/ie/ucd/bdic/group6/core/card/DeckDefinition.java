package ie.ucd.bdic.group6.core.card;

/**
 * Declarative composition of the standard 106-card play deck. {@link StandardDeckFactory} reads these constants.
 */
public final class DeckDefinition {

    public static final int TOTAL_PLAY_CARDS = 106;

    public static final int MONEY_1M = 6;
    public static final int MONEY_2M = 5;
    public static final int MONEY_3M = 3;
    public static final int MONEY_4M = 3;
    public static final int MONEY_5M = 2;
    public static final int MONEY_10M = 1;

    public static final int PROPERTY_BROWN = 2;
    public static final int PROPERTY_LIGHT_BLUE = 3;
    public static final int PROPERTY_PINK = 3;
    public static final int PROPERTY_ORANGE = 3;
    public static final int PROPERTY_RED = 3;
    public static final int PROPERTY_YELLOW = 3;
    public static final int PROPERTY_GREEN = 3;
    public static final int PROPERTY_DARK_BLUE = 2;
    public static final int PROPERTY_RAILROAD = 4;
    public static final int PROPERTY_UTILITY = 2;

    public static final int ACTION_DEAL_BREAKER = 2;
    public static final int ACTION_FORCED_DEAL = 3;
    public static final int ACTION_SLY_DEAL = 3;
    public static final int ACTION_JUST_SAY_NO = 3;
    public static final int ACTION_DEBT_COLLECTOR = 3;
    public static final int ACTION_ITS_MY_BIRTHDAY = 3;
    public static final int ACTION_DOUBLE_THE_RENT = 2;
    public static final int ACTION_HOUSE = 3;
    public static final int ACTION_HOTEL = 2;
    public static final int ACTION_PASS_GO = 10;

    public static final int RENT_DUAL_EACH = 2;
    public static final int RENT_WILD = 3;

    private DeckDefinition() {
    }
}
