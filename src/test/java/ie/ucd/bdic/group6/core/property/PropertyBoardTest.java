package ie.ucd.bdic.group6.core.property;

import ie.ucd.bdic.group6.core.card.PropertyCard;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PropertyBoardTest {

    @Test
    void autoPlayDirectsCardToExistingIncompleteRowOfSameColor() {
        PropertyBoard board = new PropertyBoard();
        PropertyCard brown1 = new PropertyCard("b1", "B1", PropertyColor.BROWN, 2);
        PropertyCard brown2 = new PropertyCard("b2", "B2", PropertyColor.BROWN, 2);

        PropertySet row = board.startNewRow(PropertyColor.BROWN);
        row.tryAdd(brown1, Optional.empty());

        var result = board.tryAddCardToRow(row, brown2, Optional.empty());
        assertTrue(result.isSuccess());
        assertEquals(1, board.rowsView().size());
        assertEquals(2, row.size());
    }

    @Test
    void startNewRowAllowsDuplicateColorWhenExistingRowIsFull() {
        PropertyBoard board = new PropertyBoard();
        PropertyCard brown1 = new PropertyCard("b1", "B1", PropertyColor.BROWN, 2);
        PropertyCard brown2 = new PropertyCard("b2", "B2", PropertyColor.BROWN, 2);
        PropertyCard brown3 = new PropertyCard("b3", "B3", PropertyColor.BROWN, 2);

        PropertySet first = board.startNewRow(PropertyColor.BROWN);
        first.tryAdd(brown1, Optional.empty());
        first.tryAdd(brown2, Optional.empty());
        assertTrue(first.isComplete());

        PropertySet second = board.startNewRow(PropertyColor.BROWN);
        second.tryAdd(brown3, Optional.empty());
        assertEquals(2, board.rowsView().size());
    }

    @Test
    void startNewRowReusesIncompleteRowOfSameColor() {
        PropertyBoard board = new PropertyBoard();
        PropertyCard brown1 = new PropertyCard("b1", "B1", PropertyColor.BROWN, 2);

        PropertySet first = board.startNewRow(PropertyColor.BROWN);
        first.tryAdd(brown1, Optional.empty());

        PropertySet second = board.startNewRow(PropertyColor.BROWN);
        assertSame(first, second, "should return the existing incomplete row");
        assertEquals(1, board.rowsView().size());
    }
}
