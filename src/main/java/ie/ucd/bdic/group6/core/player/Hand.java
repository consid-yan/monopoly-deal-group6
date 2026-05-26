package ie.ucd.bdic.group6.core.player;

import ie.ucd.bdic.group6.core.card.Card;
import ie.ucd.bdic.group6.core.common.OperationError;
import ie.ucd.bdic.group6.core.common.OperationResult;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Player hand pile. Cards here may be played or banked per turn rules; never used to pay debts directly.
 */
public final class Hand {

    private final List<Card> cards = new ArrayList<>();
    private final List<Runnable> mutateListeners = new CopyOnWriteArrayList<>();

    public void addMutationListener(Runnable listener) {
        if (listener != null) {
            mutateListeners.add(listener);
        }
    }

    public List<Card> view() {
        return Collections.unmodifiableList(cards);
    }

    /** Copy of current card references (for snapshots). */
    public List<Card> copyCards() {
        return new ArrayList<>(cards);
    }

    public int size() {
        return cards.size();
    }

    public boolean contains(Card card) {
        return cards.stream().anyMatch(c -> c.id().equals(card.id()));
    }

    public void add(Card card) {
        cards.add(Objects.requireNonNull(card, "card"));
        fireChanged();
    }

    public void addAll(Collection<Card> pile) {
        for (Card c : pile) {
            cards.add(Objects.requireNonNull(c, "card"));
        }
        fireChanged();
    }

    public boolean remove(Card card) {
        boolean ok = cards.removeIf(c -> c.id().equals(card.id()));
        if (ok) {
            fireChanged();
        }
        return ok;
    }

    /**
     * If hand exceeds {@code maxHandSize}, discards exactly {@code size - maxHandSize} cards from
     * {@code selectionOrder} (in list order) into {@code discardSink} (central discard / play pile).
     */
    public OperationResult<Void> discardDownTo(int maxHandSize, List<Card> selectionOrder, CardDiscardSink discardSink) {
        Objects.requireNonNull(selectionOrder, "selectionOrder");
        Objects.requireNonNull(discardSink, "discardSink");
        if (maxHandSize < 0) {
            return OperationResult.fail(OperationError.GENERIC, "maxHandSize must be non-negative");
        }
        int over = cards.size() - maxHandSize;
        if (over <= 0) {
            return OperationResult.ok();
        }
        List<Card> toDiscard = new ArrayList<>(over);
        for (Card pick : selectionOrder) {
            if (toDiscard.size() >= over) {
                break;
            }
            if (!contains(pick)) {
                return OperationResult.fail(OperationError.CARD_NOT_IN_HAND, "card not in hand: " + pick.id());
            }
            if (toDiscard.stream().anyMatch(c -> c.id().equals(pick.id()))) {
                continue;
            }
            toDiscard.add(pick);
        }
        if (toDiscard.size() < over) {
            return OperationResult.fail(
                    OperationError.HAND_LIMIT_EXCEEDED,
                    "need to discard " + over + " cards but selection yielded " + toDiscard.size());
        }
        for (Card c : toDiscard) {
            if (!remove(c)) {
                return OperationResult.fail(OperationError.GENERIC, "failed removing card during discard: " + c.id());
            }
            discardSink.discard(c);
        }
        return OperationResult.ok();
    }

    public void clear() {
        cards.clear();
        fireChanged();
    }

    public void replaceAll(List<Card> newOrder) {
        cards.clear();
        cards.addAll(newOrder);
        fireChanged();
    }

    private void fireChanged() {
        for (Runnable r : mutateListeners) {
            r.run();
        }
    }
}
