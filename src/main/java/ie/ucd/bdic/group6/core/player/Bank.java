package ie.ucd.bdic.group6.core.player;

import ie.ucd.bdic.group6.core.card.Card;
import ie.ucd.bdic.group6.core.card.CardBankSupport;
import ie.ucd.bdic.group6.core.common.OperationError;
import ie.ucd.bdic.group6.core.common.OperationResult;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Bank pile: only cards that may be banked as money. Action cards in the bank can never be played for effects
 * (see {@link CardBankSupport#canUseBankedCardAsAction(Card)}).
 */
public final class Bank {

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

    public List<Card> copyCards() {
        return new ArrayList<>(cards);
    }

    public int size() {
        return cards.size();
    }

    public boolean contains(Card card) {
        return cards.stream().anyMatch(c -> c.id().equals(card.id()));
    }

    public int totalValueM() {
        return cards.stream().mapToInt(CardBankSupport::bankPaymentValueM).sum();
    }

    /**
     * Adds a card to the bank if it is legal money-bank material.
     */
    public OperationResult<Void> depositAsMoney(Card card) {
        Objects.requireNonNull(card, "card");
        if (!CardBankSupport.canBankAsMoney(card)) {
            return OperationResult.fail(
                    OperationError.CANNOT_BANK_PROPERTY_CARD,
                    "only money, bankable action cards, or bankable wilds may enter the bank");
        }
        cards.add(card);
        fireChanged();
        return OperationResult.ok();
    }

    /** Test / migration helper when bypassing validation is intentional. */
    public void forceAddForTestsOrMigration(Card card) {
        cards.add(Objects.requireNonNull(card, "card"));
        fireChanged();
    }

    public boolean remove(Card card) {
        boolean ok = cards.removeIf(c -> c.id().equals(card.id()));
        if (ok) {
            fireChanged();
        }
        return ok;
    }

    public void addAll(Collection<Card> pile) {
        cards.addAll(pile);
        fireChanged();
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
