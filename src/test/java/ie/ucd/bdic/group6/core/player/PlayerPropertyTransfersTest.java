package ie.ucd.bdic.group6.core.player;

import ie.ucd.bdic.group6.core.card.PropertyCard;
import ie.ucd.bdic.group6.core.property.PropertyColor;
import ie.ucd.bdic.group6.core.property.PropertySet;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlayerPropertyTransfersTest {

    @Test
    void payPropertyCardMovesCardAndReportsRentValue() {
        Player payer = new Player("p1", "Payer");
        Player creditor = new Player("p2", "Creditor");
        PropertyCard b1 = new PropertyCard("b1", "B1", PropertyColor.BROWN, 2);
        PropertyCard b2 = new PropertyCard("b2", "B2", PropertyColor.BROWN, 2);
        PropertySet payerRow = payer.propertyBoard().startNewRow(PropertyColor.BROWN);
        assertTrue(payerRow.tryAdd(b1, Optional.empty()));
        assertTrue(payerRow.tryAdd(b2, Optional.empty()));

        PropertySet creditorRow = creditor.propertyBoard().startNewRow(PropertyColor.BROWN);
        var paid = PlayerPropertyTransfers.payPropertyCardToCreditor(
                payer, creditor, b1, creditorRow, Optional.empty());
        assertTrue(paid.isSuccess());
        assertEquals(2, paid.value().orElseThrow());
        assertTrue(creditorRow.contains(b1));
        assertEquals(1, payer.propertyBoard().rowsView().size());
    }

    @Test
    void transferEntireRowPreservesRowIdAndImprovements() {
        Player sender = new Player("s", "S");
        Player receiver = new Player("r", "R");
        PropertySet row = new PropertySet(PropertyColor.BROWN);
        String rowId = row.rowId();
        PropertyCard b1 = new PropertyCard("b1", "B1", PropertyColor.BROWN, 2);
        PropertyCard b2 = new PropertyCard("b2", "B2", PropertyColor.BROWN, 2);
        assertTrue(row.tryAdd(b1, Optional.empty()));
        assertTrue(row.tryAdd(b2, Optional.empty()));
        assertTrue(row.tryPlaceHouseStructuralOnly());
        sender.propertyBoard().addRow(row);

        var moved = PlayerPropertyTransfers.transferEntireRow(sender, receiver, row);
        assertTrue(moved.isSuccess());
        assertEquals(rowId, moved.value().orElseThrow().rowId());
        assertTrue(moved.value().orElseThrow().hasHouse());
        assertTrue(receiver.propertyBoard().findRowById(rowId).isPresent());
        assertTrue(sender.propertyBoard().rowsView().isEmpty());
    }
}
