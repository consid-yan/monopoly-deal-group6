package ie.ucd.bdic.group6.core.player;

import ie.ucd.bdic.group6.core.card.Card;

/**
 * Receives cards leaving a player's hand (discard / play pile). Implemented by the game session's
 * central discard pile so {@code core.player} does not depend on {@code core.engine}.
 */
@FunctionalInterface
public interface CardDiscardSink {

    void discard(Card card);
}
