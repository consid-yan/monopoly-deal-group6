package ie.ucd.bdic.group6.core.property;

import ie.ucd.bdic.group6.core.card.PropertyCard;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PropertyBoardRowIdTest {

    @Test
    void findRowByIdAndStealRules() {
        PropertyBoard board = new PropertyBoard();
        PropertySet row = board.startNewRow(PropertyColor.BROWN);
        String rowId = row.rowId();
        PropertyCard b1 = new PropertyCard("b1", "B1", PropertyColor.BROWN, 2);
        PropertyCard b2 = new PropertyCard("b2", "B2", PropertyColor.BROWN, 2);
        assertTrue(board.tryAddCardToRow(row, b1, Optional.empty()).isSuccess());
        assertTrue(board.canRemoveCardForSteal(b1));
        assertTrue(board.tryAddCardToRow(row, b2, Optional.empty()).isSuccess());
        assertTrue(row.isComplete());
        assertFalse(board.canRemoveCardForSteal(b1));
        assertEquals(rowId, board.findRowById(rowId).orElseThrow().rowId());
        assertTrue(board.canPayWithPropertyCard(b1));
    }
}
