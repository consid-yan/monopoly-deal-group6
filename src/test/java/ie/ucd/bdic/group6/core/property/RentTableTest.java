package ie.ucd.bdic.group6.core.property;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RentTableTest {

    @ParameterizedTest(name = "{0} with {1} card(s) = {2}M")
    @CsvSource({
            "BROWN,       1, 1",
            "BROWN,       2, 2",
            "LIGHT_BLUE,  1, 1",
            "LIGHT_BLUE,  2, 2",
            "LIGHT_BLUE,  3, 3",
            "PINK,        1, 1",
            "PINK,        2, 2",
            "PINK,        3, 4",
            "ORANGE,      1, 1",
            "ORANGE,      2, 3",
            "ORANGE,      3, 5",
            "RED,         1, 2",
            "RED,         2, 3",
            "RED,         3, 6",
            "YELLOW,      1, 2",
            "YELLOW,      2, 4",
            "YELLOW,      3, 6",
            "GREEN,       1, 2",
            "GREEN,       2, 4",
            "GREEN,       3, 7",
            "DARK_BLUE,   1, 3",
            "DARK_BLUE,   2, 8",
            "RAILROAD,    1, 1",
            "RAILROAD,    2, 2",
            "RAILROAD,    3, 3",
            "RAILROAD,    4, 4",
            "UTILITY,     1, 1",
            "UTILITY,     2, 2"
    })
    void baseRentMatchesOfficialChart(String colorName, int cards, int expectedRent) {
        PropertyColor color = PropertyColor.valueOf(colorName);
        assertEquals(expectedRent, RentTable.baseRent(color, cards),
                colorName + " with " + cards + " card(s)");
    }

    @ParameterizedTest(name = "baseRent({0}, 0) = 0")
    @CsvSource({"BROWN", "LIGHT_BLUE", "PINK", "ORANGE", "RED", "YELLOW", "GREEN", "DARK_BLUE", "RAILROAD", "UTILITY"})
    void baseRentWithZeroCardsIsZero(String colorName) {
        assertEquals(0, RentTable.baseRent(PropertyColor.valueOf(colorName), 0));
    }

    @ParameterizedTest(name = "baseRent({0}, -1) = 0")
    @CsvSource({"BROWN", "RED", "RAILROAD"})
    void baseRentWithNegativeCardsIsZero(String colorName) {
        assertEquals(0, RentTable.baseRent(PropertyColor.valueOf(colorName), -1));
    }

    @ParameterizedTest(name = "completeSetRent({0}, house={1}, hotel={2}) = {3}M")
    @CsvSource({
            "BROWN,  false, false, 2",
            "BROWN,  true,  false, 3",
            "BROWN,  true,  true,  4",
            "RED,    false, false, 6",
            "RED,    true,  false, 11",
            "RED,    true,  true,  15",
            "RAILROAD, false, false, 4",
            "RAILROAD, true,  false, 4",
            "UTILITY,  false, false, 2"
    })
    void completeSetRentWithImprovements(String colorName, boolean house, boolean hotel, int expected) {
        PropertyColor color = PropertyColor.valueOf(colorName);
        assertEquals(expected, RentTable.completeSetRent(color, house, hotel),
                colorName + " house=" + house + " hotel=" + hotel);
    }

    @org.junit.jupiter.api.Test
    void completeSetRentRejectsHotelWithoutHouse() {
        org.junit.jupiter.api.Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> RentTable.completeSetRent(PropertyColor.BROWN, false, true));
    }
}
