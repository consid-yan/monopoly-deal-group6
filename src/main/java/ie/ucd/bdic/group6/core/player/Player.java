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
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Aggregates {@link Hand}, {@link Bank}, and {@link PropertyBoard}. Multi-step transactions should be composed
 * by the engine using {@link PlayerSnapshot} plus atomic helpers such as {@link PlayerPropertyTransfers}.
 * For a stable integration surface, prefer {@link ie.ucd.bdic.group6.facade.PlayerTableFacade}.
 */
public final class Player {

    private final String id;
    private final String name;
    private final Hand hand = new Hand();
    private final Bank bank = new Bank();
    private final PropertyBoard propertyBoard = new PropertyBoard();
    private final List<PlayerZoneListener> zoneListeners = new CopyOnWriteArrayList<>();

    public Player(String id, String name) {
        this.id = Objects.requireNonNull(id, "id");
        this.name = Objects.requireNonNull(name, "name");
        hand.addMutationListener(() -> zoneListeners.forEach(l -> l.onHandChanged(this)));
        bank.addMutationListener(() -> zoneListeners.forEach(l -> l.onBankChanged(this)));
        propertyBoard.addMutationListener(() -> zoneListeners.forEach(l -> l.onPropertyBoardChanged(this)));
    }

    public void addZoneListener(PlayerZoneListener listener) {
        if (listener != null) {
            zoneListeners.add(listener);
        }
    }

    public void removeZoneListener(PlayerZoneListener listener) {
        zoneListeners.remove(listener);
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

    public boolean canDeclareVictory(boolean isMyTurn) {
        return isMyTurn && propertyBoard.completeDistinctColorCount() >= 3;
    }

    public void addToHand(Card card) {
        hand.add(card);
    }

    public void addAllToHand(Collection<Card> cards) {
        hand.addAll(cards);
    }

    public OperationResult<Void> tryBankFromHand(Card card) {
        Objects.requireNonNull(card, "card");
        if (!hand.contains(card)) {
            return OperationResult.fail(OperationError.CARD_NOT_IN_HAND, "card not in hand: " + card.id());
        }
        if (!hand.remove(card)) {
            return OperationResult.fail(OperationError.CARD_NOT_IN_HAND, "card not in hand: " + card.id());
        }
        OperationResult<Void> deposited = bank.depositAsMoney(card);
        if (!deposited.isSuccess()) {
            hand.add(card);
            return deposited;
        }
        return OperationResult.ok();
    }

    public OperationResult<Void> tryPlayPropertyFromHand(PropertySet row, Card card, Optional<PropertyColor> wildChoice) {
        Objects.requireNonNull(row, "row");
        Objects.requireNonNull(card, "card");
        if (!propertyBoard.ownsRow(row)) {
            return OperationResult.fail(OperationError.ROW_NOT_OWNED_BY_PLAYER, "row not owned by player");
        }
        if (!hand.contains(card)) {
            return OperationResult.fail(OperationError.CARD_NOT_IN_HAND, "card not in hand: " + card.id());
        }
        if (!hand.remove(card)) {
            return OperationResult.fail(OperationError.CARD_NOT_IN_HAND, "card not in hand: " + card.id());
        }
        OperationResult<Void> placed = propertyBoard.tryAddCardToRow(row, card, wildChoice);
        if (!placed.isSuccess()) {
            hand.add(card);
            return placed;
        }
        return OperationResult.ok();
    }

    public OperationResult<Void> tryStartNewPropertyRowFromHand(PropertyColor anchorColor, Card card, Optional<PropertyColor> wildChoice) {
        Objects.requireNonNull(anchorColor, "anchorColor");
        Objects.requireNonNull(card, "card");
        if (!hand.contains(card)) {
            return OperationResult.fail(OperationError.CARD_NOT_IN_HAND, "card not in hand: " + card.id());
        }
        if (!hand.remove(card)) {
            return OperationResult.fail(OperationError.CARD_NOT_IN_HAND, "card not in hand: " + card.id());
        }
        PropertySet row = propertyBoard.startNewRow(anchorColor);
        OperationResult<Void> placed = propertyBoard.tryAddCardToRow(row, card, wildChoice);
        if (!placed.isSuccess()) {
            hand.add(card);
            if (row.size() == 0) {
                propertyBoard.removeRow(row);
                propertyBoard.compactEmptyRows();
            }
            return placed;
        }
        return OperationResult.ok();
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
            java.util.function.BooleanSupplier placeStructural) {
        Objects.requireNonNull(actionFromHand, "actionFromHand");
        Objects.requireNonNull(row, "row");
        if (actionFromHand.actionType() != expected) {
            return OperationResult.fail(OperationError.WRONG_ACTION_CARD_TYPE, "expected " + expected);
        }
        if (!propertyBoard.ownsRow(row)) {
            return OperationResult.fail(OperationError.ROW_NOT_OWNED_BY_PLAYER, "row not owned by player");
        }
        if (!hand.contains(actionFromHand)) {
            return OperationResult.fail(OperationError.CARD_NOT_IN_HAND, "action card not in hand");
        }
        if (!hand.remove(actionFromHand)) {
            return OperationResult.fail(OperationError.CARD_NOT_IN_HAND, "action card not in hand");
        }
        if (!placeStructural.getAsBoolean()) {
            hand.add(actionFromHand);
            return OperationResult.fail(
                    OperationError.NOT_COMPLETE_SET, "row cannot accept " + expected + " improvement right now");
        }
        return OperationResult.ok(actionFromHand);
    }

    public OperationResult<ActionCard> trySpendHouseCardOnRowById(ActionCard houseFromHand, String rowId) {
        return findPropertyRow(rowId)
                .map(row -> trySpendHouseCardOnRow(houseFromHand, row))
                .orElseGet(() -> OperationResult.fail(
                        OperationError.PROPERTY_ROW_NOT_FOUND, "no property row with id " + rowId));
    }

    public OperationResult<ActionCard> trySpendHotelCardOnRowById(ActionCard hotelFromHand, String rowId) {
        return findPropertyRow(rowId)
                .map(row -> trySpendHotelCardOnRow(hotelFromHand, row))
                .orElseGet(() -> OperationResult.fail(
                        OperationError.PROPERTY_ROW_NOT_FOUND, "no property row with id " + rowId));
    }

    public OperationResult<Void> tryDiscardHandDownTo(int maxHandSize, List<Card> selectionOrder, CardDiscardSink discardSink) {
        return hand.discardDownTo(maxHandSize, selectionOrder, discardSink);
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

    public PlayerSnapshot snapshot() {
        return PlayerSnapshot.capture(this);
    }

    public void restore(PlayerSnapshot snap) {
        PlayerSnapshot.restore(this, snap);
    }
}
