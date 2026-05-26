package ie.ucd.bdic.group6.facade;

import ie.ucd.bdic.group6.core.card.ActionCard;
import ie.ucd.bdic.group6.core.card.Card;
import ie.ucd.bdic.group6.core.common.OperationResult;
import ie.ucd.bdic.group6.core.player.BankPaymentSolver;
import ie.ucd.bdic.group6.core.player.CardDiscardSink;
import ie.ucd.bdic.group6.core.player.Player;
import ie.ucd.bdic.group6.core.player.PlayerSnapshot;
import ie.ucd.bdic.group6.core.property.PropertyColor;
import ie.ucd.bdic.group6.core.property.PropertySet;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Facade over {@link Player} table zones (hand, bank, property board) for engine/UI integration.
 * Command-level orchestration remains in {@link GameFacade}; this class exposes stable player-table operations.
 */
public record PlayerTableFacade(Player player) {

    public OperationResult<Void> bankFromHand(Card card) {
        return player.tryBankFromHand(card);
    }

    public OperationResult<Void> playPropertyFromHand(PropertySet row, Card card, Optional<PropertyColor> wildChoice) {
        return player.tryPlayPropertyFromHand(row, card, wildChoice);
    }

    public OperationResult<Void> startPropertyRowFromHand(PropertyColor anchor, Card card, Optional<PropertyColor> wildChoice) {
        return player.tryStartNewPropertyRowFromHand(anchor, card, wildChoice);
    }

    public OperationResult<ActionCard> spendHouseOnRow(ActionCard houseFromHand, PropertySet row) {
        return player.trySpendHouseCardOnRow(houseFromHand, row);
    }

    public OperationResult<ActionCard> spendHotelOnRow(ActionCard hotelFromHand, PropertySet row) {
        return player.trySpendHotelCardOnRow(hotelFromHand, row);
    }

    public OperationResult<Void> discardHandDownToSeven(List<Card> discardSelection, CardDiscardSink discardSink) {
        return player.tryDiscardHandDownTo(7, discardSelection, discardSink);
    }

    public OperationResult<Void> payBankMoneyTo(Player receiver, Collection<Card> bankCards) {
        return player.tryPayBankCardsTo(receiver, bankCards);
    }

    public OperationResult<List<Card>> suggestBankPayment(int dueM) {
        return BankPaymentSolver.suggestMinimalOverpaySubset(player.bank().view(), dueM);
    }

    public boolean canCoverBankPayment(int dueM) {
        return BankPaymentSolver.canCoverWithBank(player.bank().view(), dueM);
    }

    public boolean canDeclareVictory(boolean isMyTurn) {
        return player.canDeclareVictory(isMyTurn);
    }

    public PlayerSnapshot snapshot() {
        return player.snapshot();
    }

    public void restore(PlayerSnapshot snap) {
        player.restore(snap);
    }
}
