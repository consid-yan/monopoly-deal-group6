package ie.ucd.bdic.group6.core.player;

import ie.ucd.bdic.group6.core.card.PropertyCard;
import ie.ucd.bdic.group6.core.card.PropertyWildCard;
import ie.ucd.bdic.group6.core.common.OperationError;
import ie.ucd.bdic.group6.core.common.OperationResult;
import ie.ucd.bdic.group6.core.property.PropertyColor;
import ie.ucd.bdic.group6.core.property.PropertySet;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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

    @Test
    void successfulTransferMovesCardBetweenPlayers() {
        Player sender = new Player("s", "Sender");
        Player receiver = new Player("r", "Receiver");
        PropertyCard card = new PropertyCard("p1", "Brown 1", PropertyColor.BROWN, 2);
        sender.addToHand(card);
        sender.tryStartNewPropertyRowFromHand(PropertyColor.BROWN, card, Optional.empty());

        PropertySet receiverRow = receiver.propertyBoard().startNewRow(PropertyColor.BROWN);
        OperationResult<Void> result = PlayerPropertyTransfers.transferPropertyCardToRow(
                sender, receiver, card, receiverRow, Optional.empty());

        assertTrue(result.isSuccess());
        assertFalse(sender.propertyBoard().findRowContaining(card).isPresent());
        assertTrue(receiverRow.contains(card));
    }

    @Test
    void transferFailsWhenCardNotOnSenderBoard() {
        Player sender = new Player("s", "Sender");
        Player receiver = new Player("r", "Receiver");
        PropertyCard card = new PropertyCard("p1", "Brown 1", PropertyColor.BROWN, 2);
        PropertySet receiverRow = receiver.propertyBoard().startNewRow(PropertyColor.BROWN);

        OperationResult<Void> result = PlayerPropertyTransfers.transferPropertyCardToRow(
                sender, receiver, card, receiverRow, Optional.empty());

        assertFalse(result.isSuccess());
        assertEquals(OperationError.CARD_NOT_ON_PROPERTY_BOARD, result.errorCode().orElseThrow());
    }

    @Test
    void transferFailsWhenReceiverRowRejectsCard() {
        Player sender = new Player("s", "Sender");
        Player receiver = new Player("r", "Receiver");
        PropertyCard brown = new PropertyCard("p1", "Brown 1", PropertyColor.BROWN, 2);
        sender.addToHand(brown);
        sender.tryStartNewPropertyRowFromHand(PropertyColor.BROWN, brown, Optional.empty());

        PropertySet redRow = receiver.propertyBoard().startNewRow(PropertyColor.RED);
        OperationResult<Void> result = PlayerPropertyTransfers.transferPropertyCardToRow(
                sender, receiver, brown, redRow, Optional.empty());

        assertFalse(result.isSuccess());
        assertEquals(OperationError.RECEIVER_ROW_REJECTS_CARD, result.errorCode().orElseThrow());
        assertTrue(sender.propertyBoard().findRowContaining(brown).isPresent());
    }

    @Test
    void transferFailsWhenReceiverRowNotOwnedByReceiver() {
        Player sender = new Player("s", "Sender");
        Player receiver = new Player("r", "Receiver");
        PropertyCard card = new PropertyCard("p1", "Brown 1", PropertyColor.BROWN, 2);
        sender.addToHand(card);
        sender.tryStartNewPropertyRowFromHand(PropertyColor.BROWN, card, Optional.empty());

        PropertySet orphanRow = new PropertySet(PropertyColor.BROWN);
        OperationResult<Void> result = PlayerPropertyTransfers.transferPropertyCardToRow(
                sender, receiver, card, orphanRow, Optional.empty());

        assertFalse(result.isSuccess());
        assertEquals(OperationError.ROW_NOT_OWNED_BY_PLAYER, result.errorCode().orElseThrow());
    }

    @Test
    void wildCardTransferPreservesColorAssignment() {
        Player sender = new Player("s", "Sender");
        Player receiver = new Player("r", "Receiver");
        PropertyWildCard wild = new PropertyWildCard("w1", "P/O",
                EnumSet.of(PropertyColor.PINK, PropertyColor.ORANGE), 2);
        sender.addToHand(wild);
        sender.tryStartNewPropertyRowFromHand(PropertyColor.PINK, wild, Optional.of(PropertyColor.PINK));

        PropertySet receiverRow = receiver.propertyBoard().startNewRow(PropertyColor.PINK);
        OperationResult<Void> result = PlayerPropertyTransfers.transferPropertyCardToRow(
                sender, receiver, wild, receiverRow, Optional.of(PropertyColor.PINK));

        assertTrue(result.isSuccess());
        assertTrue(receiverRow.contains(wild));
        assertEquals(PropertyColor.PINK, receiverRow.wildAssignmentFor(wild.id()).orElseThrow());
    }
}
