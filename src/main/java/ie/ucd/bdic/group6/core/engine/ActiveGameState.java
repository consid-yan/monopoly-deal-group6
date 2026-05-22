package ie.ucd.bdic.group6.core.engine;

import ie.ucd.bdic.group6.core.card.Card;
import ie.ucd.bdic.group6.core.card.Deck;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public final class ActiveGameState {
    private final Deck drawPile;
    private final List<Card> discardPile = new ArrayList<>();
    private final TurnManager turnManager;
    // TODO(engine-state): Add PendingDebt, PendingTrade, and reconnect/disconnect state for full gameplay.
    private String winnerPlayerId;

    public ActiveGameState(Deck drawPile, TurnManager turnManager) {
        this.drawPile = Objects.requireNonNull(drawPile, "drawPile");
        this.turnManager = Objects.requireNonNull(turnManager, "turnManager");
    }

    public Deck drawPile() {
        return drawPile;
    }

    public TurnManager turnManager() {
        return turnManager;
    }

    public void discard(Card card) {
        discardPile.add(Objects.requireNonNull(card, "card"));
    }

    public List<Card> discardPileView() {
        return List.copyOf(discardPile);
    }

    public int discardPileSize() {
        return discardPile.size();
    }

    // TODO(engine-deck): Define deck exhaustion and discard reshuffle rules before advanced draw effects.

    public Optional<String> winnerPlayerId() {
        return Optional.ofNullable(winnerPlayerId);
    }

    void setWinnerPlayerId(String winnerPlayerId) {
        this.winnerPlayerId = Objects.requireNonNull(winnerPlayerId, "winnerPlayerId");
    }
}
