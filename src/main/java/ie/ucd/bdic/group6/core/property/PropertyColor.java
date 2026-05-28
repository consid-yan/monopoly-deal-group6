package ie.ucd.bdic.group6.core.property;

/**
 * Monopoly Deal property colors (including railroads and utilities).
 */
public enum PropertyColor {
    BROWN,
    LIGHT_BLUE,
    PINK,
    ORANGE,
    RED,
    YELLOW,
    GREEN,
    DARK_BLUE,
    RAILROAD,
    UTILITY;

    /**
     * Number of property cards required to complete one full set for this color.
     */
    public static int fullSetSize(PropertyColor color) {
        return switch (color) {
            case BROWN, DARK_BLUE, UTILITY -> 2;
            case RAILROAD -> 4;
            case LIGHT_BLUE, PINK, ORANGE, RED, YELLOW, GREEN -> 3;
        };
    }

    public static boolean isRailOrUtility(PropertyColor color) {
        return color == RAILROAD || color == UTILITY;
    }
}
