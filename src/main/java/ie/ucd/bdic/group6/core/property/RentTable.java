package ie.ucd.bdic.group6.core.property;

import java.util.EnumMap;
import java.util.Map;

/**
 * Official-style Monopoly Deal rent amounts in millions (M). Values follow commonly published charts;
 * engine/UI may refine per edition.
 */
public final class RentTable {

    private RentTable() {
    }

    /**
     * Rent for a row with {@code cards} property cards placed (including wilds counting as one card each),
     * before the set is complete. When {@code cards == PropertyColor.fullSetSize(color)}, this equals the
     * full-set base rent (no house/hotel).
     */
    public static int baseRent(PropertyColor color, int cards) {
        if (cards <= 0) {
            return 0;
        }
        int cap = PropertyColor.fullSetSize(color);
        int n = Math.min(cards, cap);
        return switch (color) {
            case BROWN -> n == 1 ? 1 : 2;
            case LIGHT_BLUE -> n == 1 ? 1 : (n == 2 ? 2 : 3);
            case PINK -> n == 1 ? 1 : (n == 2 ? 2 : 4);
            case ORANGE -> n == 1 ? 1 : (n == 2 ? 3 : 5);
            case RED -> n == 1 ? 2 : (n == 2 ? 3 : 6);
            case YELLOW -> n == 1 ? 2 : (n == 2 ? 4 : 7);
            case GREEN -> n == 1 ? 3 : (n == 2 ? 5 : 8);
            case DARK_BLUE -> n == 1 ? 3 : 8;
            case RAILROAD -> switch (n) {
                case 1 -> 1;
                case 2 -> 2;
                case 3 -> 3;
                default -> 7;
            };
            case UTILITY -> n == 1 ? 1 : 2;
        };
    }

    /**
     * Total rent for a <strong>complete</strong> set including at most one house and one hotel.
     * Railroads/utilities cannot be improved; {@code house} / {@code hotel} are ignored for them.
     */
    public static int completeSetRent(PropertyColor color, boolean house, boolean hotel) {
        if (PropertyColor.isRailOrUtility(color)) {
            return baseRent(color, PropertyColor.fullSetSize(color));
        }
        if (hotel) {
            return HOTEL_RENT.get(color);
        }
        if (house) {
            return HOUSE_RENT.get(color);
        }
        return baseRent(color, PropertyColor.fullSetSize(color));
    }

    private static final Map<PropertyColor, Integer> HOUSE_RENT = houseRentMap();
    private static final Map<PropertyColor, Integer> HOTEL_RENT = hotelRentMap();

    private static EnumMap<PropertyColor, Integer> houseRentMap() {
        EnumMap<PropertyColor, Integer> m = new EnumMap<>(PropertyColor.class);
        m.put(PropertyColor.BROWN, 3);
        m.put(PropertyColor.LIGHT_BLUE, 4);
        m.put(PropertyColor.PINK, 7);
        m.put(PropertyColor.ORANGE, 9);
        m.put(PropertyColor.RED, 11);
        m.put(PropertyColor.YELLOW, 13);
        m.put(PropertyColor.GREEN, 16);
        m.put(PropertyColor.DARK_BLUE, 11);
        return m;
    }

    private static EnumMap<PropertyColor, Integer> hotelRentMap() {
        EnumMap<PropertyColor, Integer> m = new EnumMap<>(PropertyColor.class);
        m.put(PropertyColor.BROWN, 4);
        m.put(PropertyColor.LIGHT_BLUE, 8);
        m.put(PropertyColor.PINK, 11);
        m.put(PropertyColor.ORANGE, 13);
        m.put(PropertyColor.RED, 15);
        m.put(PropertyColor.YELLOW, 17);
        m.put(PropertyColor.GREEN, 20);
        m.put(PropertyColor.DARK_BLUE, 17);
        return m;
    }
}
