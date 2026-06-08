package ie.ucd.bdic.group6.core.property;

import ie.ucd.bdic.group6.core.card.PropertyCard;
import ie.ucd.bdic.group6.core.rules.PropertyRentRule;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PropertyRentRuleTest {

    @Test
    void maxRentForAnchorUsesIncompleteRows() {
        PropertyBoard board = new PropertyBoard();
        PropertySet row = board.startNewRow(PropertyColor.RED);
        assertTrue(row.tryAdd(new PropertyCard("red-1", "Red 1", PropertyColor.RED, 3)));

        assertEquals(2, PropertyRentRule.maxRentForAnchor(board, PropertyColor.RED));
    }

    @Test
    void maxRentForAnchorChoosesBestRowForDuplicateColor() {
        PropertyBoard board = new PropertyBoard();
        PropertySet complete = board.startNewRow(PropertyColor.BROWN);
        assertTrue(complete.tryAdd(new PropertyCard("brown-1", "Brown 1", PropertyColor.BROWN, 2)));
        assertTrue(complete.tryAdd(new PropertyCard("brown-2", "Brown 2", PropertyColor.BROWN, 2)));
        assertTrue(complete.tryPlaceHouseStructuralOnly().isSuccess());

        PropertySet partial = board.startNewRow(PropertyColor.BROWN);
        assertTrue(partial.tryAdd(new PropertyCard("brown-3", "Brown 3", PropertyColor.BROWN, 2)));

        assertEquals(3, PropertyRentRule.maxRentForAnchor(board, PropertyColor.BROWN));
    }

    @Test
    void rentableAnchorsIgnoreEmptyRows() {
        PropertyBoard board = new PropertyBoard();
        board.startNewRow(PropertyColor.GREEN);
        PropertySet red = board.startNewRow(PropertyColor.RED);
        assertTrue(red.tryAdd(new PropertyCard("red-1", "Red 1", PropertyColor.RED, 3)));

        assertEquals(List.of(PropertyColor.RED), PropertyRentRule.rentableAnchors(board));
    }
}
