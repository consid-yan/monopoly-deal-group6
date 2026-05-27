package ie.ucd.bdic.group6.core.card;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Random;

/**
 * Draw pile with {@link #draw()} from the <strong>top</strong>.
 *
 * <p><strong>Physical mapping:</strong> the internal deque stores cards from <em>bottom</em> (front) to
 * <em>top</em> (back). Therefore:
 * <ul>
 *   <li>{@link #Deck(List)} expects {@code bottomToTop.get(0)} to be the deck bottom and the last list element
 *       to be the deck top.</li>
 *   <li>{@link #draw()} removes from the deque tail (top).</li>
 * </ul>
 */
public final class Deck {

    private final Deque<Card> cards;

    /**
     * @param bottomToTop cards ordered from deck bottom (index 0) to deck top (last index)
     */
    public Deck(List<Card> bottomToTop) {
        Objects.requireNonNull(bottomToTop, "bottomToTop");
        this.cards = new ArrayDeque<>();
        for (Card c : bottomToTop) {
            this.cards.addLast(c);
        }
    }

    public static Deck shuffled(List<Card> cards, Random random) {
        List<Card> copy = new ArrayList<>(cards);
        Collections.shuffle(copy, random);
        return new Deck(copy);
    }

    public static Deck shuffled(List<Card> cards) {
        return shuffled(cards, new Random());
    }

    public int size() {
        return cards.size();
    }

    public boolean isEmpty() {
        return cards.isEmpty();
    }

    public Card draw() {
        if (cards.isEmpty()) {
            throw new IllegalStateException("draw from empty deck");
        }
        return cards.removeLast();
    }

    /**
     * Same as {@link #draw()} but returns empty when the pile is empty (late-game special draws may use this).
     */
    public Optional<Card> tryDraw() {
        if (cards.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(cards.removeLast());
    }

    public void discardToBottom(Card card) {
        Objects.requireNonNull(card, "card");
        cards.addFirst(card);
    }

    /**
     * Discards in chronological order: first discarded card becomes deepest at the bottom of the draw pile.
     */
    public void discardAllToBottom(Collection<Card> chronologicalDiscards) {
        Objects.requireNonNull(chronologicalDiscards, "chronologicalDiscards");
        List<Card> list = chronologicalDiscards instanceof List<Card> l ? new ArrayList<>(l) : new ArrayList<>(chronologicalDiscards);
        for (int i = list.size() - 1; i >= 0; i--) {
            discardToBottom(list.get(i));
        }
    }

    public void shuffle(Random random) {
        List<Card> tmp = new ArrayList<>(cards);
        Collections.shuffle(tmp, random);
        cards.clear();
        for (Card c : tmp) {
            cards.addLast(c);
        }
    }

    public void shuffle() {
        shuffle(new Random());
    }

    /**
     * Rebuilds an empty draw pile from {@code source}, shuffled. Used when the draw pile is exhausted and the
     * session discard pile is reshuffled into a new draw pile (see {@link DrawPileRules}).
     */
    public void refillFromShuffled(Collection<Card> source, Random random) {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(random, "random");
        if (!cards.isEmpty()) {
            throw new IllegalStateException("refill only allowed when draw pile is empty");
        }
        List<Card> copy = new ArrayList<>(source);
        Collections.shuffle(copy, random);
        for (Card c : copy) {
            cards.addLast(c);
        }
    }
}
