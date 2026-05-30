package ie.ucd.bdic.group6.core.property;

import ie.ucd.bdic.group6.core.card.PropertyCard;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PropertyRentSupportTest {

    @Test
    void maxRentForAnchorUsesIncompleteRows() {
        PropertyBoard board = new PropertyBoard();
        PropertySet row = board.startNewRow(PropertyColor.RED);
        assertTrue(row.tryAdd(new PropertyCard("red-1", "Red 1", PropertyColor.RED, 3), Optional.empty()));

        assertEquals(2, PropertyRentSupport.maxRentForAnchor(board, PropertyColor.RED));
    }

    @Test
    void maxRentForAnchorChoosesBestRowForDuplicateColor() {
        PropertyBoard board = new PropertyBoard();
        PropertySet complete = board.startNewRow(PropertyColor.BROWN);
        assertTrue(complete.tryAdd(new PropertyCard("brown-1", "Brown 1", PropertyColor.BROWN, 2), Optional.empty()));
        assertTrue(complete.tryAdd(new PropertyCard("brown-2", "Brown 2", PropertyColor.BROWN, 2), Optional.empty()));
        assertTrue(complete.tryPlaceHouseStructuralOnly());

        PropertySet partial = board.startNewRow(PropertyColor.BROWN);
        assertTrue(partial.tryAdd(new PropertyCard("brown-3", "Brown 3", PropertyColor.BROWN, 2), Optional.empty()));

        assertEquals(3, PropertyRentSupport.maxRentForAnchor(board, PropertyColor.BROWN));
    }

    @Test
    void rentableAnchorsIgnoreEmptyRows() {
        PropertyBoard board = new PropertyBoard();
        board.startNewRow(PropertyColor.GREEN);
        PropertySet red = board.startNewRow(PropertyColor.RED);
        assertTrue(red.tryAdd(new PropertyCard("red-1", "Red 1", PropertyColor.RED, 3), Optional.empty()));

        assertEquals(List.of(PropertyColor.RED), PropertyRentSupport.rentableAnchors(board));
    }
}
