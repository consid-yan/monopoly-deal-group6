package ie.ucd.bdic.group6.core.player;

import ie.ucd.bdic.group6.core.card.Card;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Player hand pile. Cards here may be played or banked per turn rules; never used to pay debts directly.
 */
public final class Hand {

    private final List<Card> cards = new ArrayList<>();

    public List<Card> view() {
        return Collections.unmodifiableList(cards);
    }

    public int size() {
        return cards.size();
    }

    public boolean contains(Card card) {
        return cards.stream().anyMatch(c -> c.id().equals(card.id()));
    }

    public void add(Card card) {
        cards.add(Objects.requireNonNull(card, "card"));
    }

    public boolean remove(Card card) {
        return cards.removeIf(c -> c.id().equals(card.id()));
    }

    public void clear() {
        cards.clear();
    }

    public void replaceAll(List<Card> newOrder) {
        cards.clear();
        cards.addAll(newOrder);
    }
}
