package ie.ucd.bdic.group6.core.card;

/**
 * Action card kinds. Rent cards use {@link ActionType} values prefixed with {@code RENT_}.
 */
public enum ActionType {
    DEAL_BREAKER,
    FORCED_DEAL,
    SLY_DEAL,
    JUST_SAY_NO,
    DEBT_COLLECTOR,
    ITS_MY_BIRTHDAY,
    DOUBLE_THE_RENT,
    HOUSE,
    HOTEL,
    PASS_GO,

    // Dual-color rent: brown or light blue (2 in deck).
    RENT_BROWN_OR_LIGHT_BLUE,
    // Dual-color rent: pink or orange (2 in deck).
    RENT_PINK_OR_ORANGE,
    // Dual-color rent: red or yellow (2 in deck).
    RENT_RED_OR_YELLOW,
    // Dual-color rent: dark blue or green (2 in deck).
    RENT_DARK_BLUE_OR_GREEN,
    // Dual-color rent: railroad or utility (2 in deck).
    RENT_RAILROAD_OR_UTILITY,
    // Wild rent: choose one color you own and charge one player (3 in deck).
    // Distinct from dual-color rent cards.
    WILD_RENT
}
