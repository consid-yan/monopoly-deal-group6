package ie.ucd.bdic.group6.core.player;

import ie.ucd.bdic.group6.core.card.ActionCard;
import ie.ucd.bdic.group6.core.card.ActionType;
import ie.ucd.bdic.group6.core.card.Card;
import ie.ucd.bdic.group6.core.common.OperationError;
import ie.ucd.bdic.group6.core.common.OperationResult;
import ie.ucd.bdic.group6.core.property.PropertyBoard;
import ie.ucd.bdic.group6.core.property.PropertyColor;
import ie.ucd.bdic.group6.core.property.PropertySet;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Aggregates {@link Hand}, {@link Bank}, and {@link PropertyBoard}. Multistep transactions should be composed
 * by the engine using {@link PlayerRollbackSnapshot} plus atomic helpers such as {@link PropertyTransferService}.
 */
public final class Player {

    private final String id;
    private final String name;
    private final Hand hand = new Hand();
    private final Bank bank = new Bank();
    private final PropertyBoard propertyBoard = new PropertyBoard();

    public Player(String id, String name) {
        this.id = Objects.requireNonNull(id, "id");
        this.name = Objects.requireNonNull(name, "name");
    }

    public String id() {
        return id;
    }

    public String name() {
        return name;
    }

    public Hand hand() {
        return hand;
    }

    public Bank bank() {
        return bank;
    }

    public PropertyBoard propertyBoard() {
        return propertyBoard;
    }

    public Optional<PropertySet> findPropertyRow(String rowId) {
        return propertyBoard.findRowById(rowId);
    }

    public List<Card> handView() {
        return hand.view();
    }

    public List<Card> bankView() {
        return bank.view();
    }

    public int bankTotalValueM() {
        return bank.totalValueM();
    }

    public boolean canDeclareVictory() {
        return propertyBoard.completeDistinctColorCount() >= 3;
    }

    public void addToHand(Card card) {
        hand.add(card);
    }

    public OperationResult<Void> tryBankFromHand(Card card) {
        if (!tryRemoveFromHand(card))
            return OperationResult.fail(OperationError.CARD_NOT_IN_HAND, "card not in hand: " + card.id());
        OperationResult<Void> deposited = bank.depositAsMoney(card);
        if (!deposited.isSuccess()) {
            hand.add(card);
        }
        return deposited;
    }

    public void tryPlayPropertyFromHand(PropertySet row, Card card) {
        tryPlayPropertyFromHand(row, card, null);
    }

    public OperationResult<Void> tryPlayPropertyFromHand(PropertySet row, Card card, PropertyColor wildChoice) {
        Objects.requireNonNull(row, "row");
        Objects.requireNonNull(card, "card");
        if (!propertyBoard.ownsRow(row)) {
            return OperationResult.fail(OperationError.ROW_NOT_OWNED_BY_PLAYER, "row not owned by player");
        }
        if (!hand.remove(card)) {
            return OperationResult.fail(OperationError.CARD_NOT_IN_HAND, "card not in hand: " + card.id());
        }
        OperationResult<Void> placed = propertyBoard.tryAddCardToRow(row, card, wildChoice);
        if (!placed.isSuccess()) {
            hand.add(card);
        }
        return placed;
    }

    public void tryStartNewPropertyRowFromHand(PropertyColor anchorColor, Card card) {
        tryStartNewPropertyRowFromHand(anchorColor, card, null);
    }

    public OperationResult<Void> tryStartNewPropertyRowFromHand(PropertyColor anchorColor, Card card, PropertyColor wildChoice) {
        Objects.requireNonNull(anchorColor, "anchorColor");
        if (!tryRemoveFromHand(card))
            return OperationResult.fail(OperationError.CARD_NOT_IN_HAND, "card not in hand: " + card.id());
        PropertySet row = propertyBoard.startNewRow(anchorColor);
        OperationResult<Void> placed = propertyBoard.tryAddCardToRow(row, card, wildChoice);
        if (!placed.isSuccess()) {
            hand.add(card);
            if (row.size() == 0) {
                propertyBoard.compactEmptyRows();
            }
        }
        return placed;
    }

    private boolean tryRemoveFromHand(Card card) {
        Objects.requireNonNull(card, "card");
        return hand.remove(card);
    }

    public OperationResult<ActionCard> trySpendHouseCardOnRow(ActionCard houseFromHand, PropertySet row) {
        return spendImprovementCard(houseFromHand, row, ActionType.HOUSE, row::tryPlaceHouseStructuralOnly);
    }

    public OperationResult<ActionCard> trySpendHotelCardOnRow(ActionCard hotelFromHand, PropertySet row) {
        return spendImprovementCard(hotelFromHand, row, ActionType.HOTEL, row::tryPlaceHotelStructuralOnly);
    }

    private OperationResult<ActionCard> spendImprovementCard(
            ActionCard actionFromHand,
            PropertySet row,
            ActionType expected,
            Supplier<OperationResult<Void>> placeStructural) {
        Objects.requireNonNull(actionFromHand, "actionFromHand");
        Objects.requireNonNull(row, "row");
        if (actionFromHand.actionType() != expected) {
            return OperationResult.fail(OperationError.WRONG_ACTION_CARD_TYPE, "expected " + expected);
        }
        if (!propertyBoard.ownsRow(row)) {
            return OperationResult.fail(OperationError.ROW_NOT_OWNED_BY_PLAYER, "row not owned by player");
        }
        if (!hand.remove(actionFromHand)) {
            return OperationResult.fail(OperationError.CARD_NOT_IN_HAND, "action card not in hand");
        }
        OperationResult<Void> placed = placeStructural.get();
        if (!placed.isSuccess()) {
            hand.add(actionFromHand);
            return OperationResult.fail(
                    placed.errorCode().orElse(OperationError.INTERNAL_ERROR),
                    placed.errorMessage().orElse("row cannot accept " + expected + " improvement right now"));
        }
        return OperationResult.ok(actionFromHand);
    }

    /**
     * Pays from this bank to another player's bank. Only cards with a bank monetary value may be used.
     */
    public OperationResult<Void> tryPayBankCardsTo(Player receiver, Collection<Card> cards) {
        Objects.requireNonNull(receiver, "receiver");
        Objects.requireNonNull(cards, "cards");
        List<Card> list = new ArrayList<>(cards);
        for (Card c : list) {
            if (!bank.contains(c)) {
                return OperationResult.fail(OperationError.CARD_NOT_IN_BANK, "missing bank card: " + c.id());
            }
            if (c.bankValueM().isEmpty()) {
                return OperationResult.fail(
                        OperationError.CANNOT_BANK_RAINBOW_WILD,
                        "card has no bank value and cannot be used as bank payment: " + c.id());
            }
        }
        List<Card> taken = new ArrayList<>();
        for (Card c : list) {
            if (!bank.remove(c)) {
                bank.addAll(taken);
                return OperationResult.fail(OperationError.CARD_NOT_IN_BANK, "missing bank card: " + c.id());
            }
            taken.add(c);
        }
        List<Card> deposited = new ArrayList<>();
        for (Card c : taken) {
            OperationResult<Void> dep = receiver.bank().depositAsMoney(c);
            if (!dep.isSuccess()) {
                for (Card x : deposited) {
                    receiver.bank().remove(x);
                }
                bank.addAll(taken);
                return dep;
            }
            deposited.add(c);
        }
        return OperationResult.ok();
    }

    public PlayerRollbackSnapshot snapshot() {
        return PlayerRollbackSnapshot.capture(this);
    }

    public void restore(PlayerRollbackSnapshot snap) {
        PlayerRollbackSnapshot.restore(this, snap);
    }
}
