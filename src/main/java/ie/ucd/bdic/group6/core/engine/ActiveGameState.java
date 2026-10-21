package ie.ucd.bdic.group6.core.engine;

import ie.ucd.bdic.group6.core.card.Card;
import ie.ucd.bdic.group6.core.card.deck.Deck;
import ie.ucd.bdic.group6.core.engine.pending.PendingAction;
import ie.ucd.bdic.group6.core.engine.pending.PendingDebt;
import ie.ucd.bdic.group6.core.engine.pending.PendingTrade;
import ie.ucd.bdic.group6.core.rules.DrawPileRule;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

public final class ActiveGameState {
    private final Deck drawPile;
    private final List<Card> discardPile = new ArrayList<>();
    private final TurnManager turnManager;
    private final List<PendingDebt> pendingDebts = new ArrayList<>();
    private final List<PendingTrade> pendingTrades = new ArrayList<>();
    private final List<PendingAction> pendingActions = new ArrayList<>();
    private String winnerPlayerId;
    private boolean endTurnRequested;

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

    public int discardPileSize() {
        return discardPile.size();
    }

    /**
     * [Deep Dive: Deck Recycling — What happens when the draw pile runs out?]
     * When the draw pile is empty and someone needs to draw, the discard pile is shuffled
     * and becomes the new draw pile. This is standard Monopoly Deal rules:
     * the discard pile is recycled as a fresh shuffled deck.
     * DrawPileRule refills the draw pile and clears the discard pile.
     * If both piles are empty, draws simply return no cards.
     */
    public void reshuffleDiscardIntoDraw() {
        DrawPileRule.reshuffleDiscardIntoDrawPile(drawPile, discardPile, ThreadLocalRandom.current());
    }

    public Optional<String> winnerPlayerId() {
        return Optional.ofNullable(winnerPlayerId);
    }

    public boolean endTurnRequested() {
        return endTurnRequested;
    }

    public void requestEndTurn() {
        endTurnRequested = true;
    }

    public void clearEndTurnRequest() {
        endTurnRequested = false;
    }

    void setWinnerPlayerId(String winnerPlayerId) {
        this.winnerPlayerId = Objects.requireNonNull(winnerPlayerId, "winnerPlayerId");
    }

    public void addPendingDebt(PendingDebt debt) {
        pendingDebts.add(Objects.requireNonNull(debt, "debt"));
    }

    public void removePendingDebt(PendingDebt debt) {
        pendingDebts.remove(debt);
    }

    public List<PendingDebt> pendingDebtsView() {
        return List.copyOf(pendingDebts);
    }

    public Optional<PendingDebt> findPendingDebt(String debtId) {
        if (debtId == null || debtId.isBlank()) {
            return Optional.empty();
        }
        return pendingDebts.stream()
                .filter(debt -> debt.debtId().equals(debtId))
                .findFirst();
    }

    public List<PendingDebt> pendingDebtsForDebtor(String debtorPlayerId) {
        return pendingDebts.stream()
                .filter(debt -> debt.isPending() && debt.debtorPlayerId().equals(debtorPlayerId))
                .toList();
    }

    public List<PendingDebt> pendingDebtsForCreditor(String creditorPlayerId) {
        return pendingDebts.stream()
                .filter(debt -> debt.isPending() && debt.creditorPlayerId().equals(creditorPlayerId))
                .toList();
    }

    public void clearAllDebtsForPlayer(String playerId) {
        pendingDebts.removeIf(debt -> debt.debtorPlayerId().equals(playerId)
                || debt.creditorPlayerId().equals(playerId));
    }

    public void addPendingTrade(PendingTrade trade) {
        pendingTrades.add(Objects.requireNonNull(trade, "trade"));
    }

    public void removePendingTrade(PendingTrade trade) {
        pendingTrades.remove(trade);
    }

    public List<PendingTrade> pendingTradesView() {
        return List.copyOf(pendingTrades);
    }

    public Optional<PendingTrade> findPendingTrade(String tradeId) {
        if (tradeId == null || tradeId.isBlank()) {
            return Optional.empty();
        }
        return pendingTrades.stream()
                .filter(trade -> trade.tradeId().equals(tradeId))
                .findFirst();
    }

    public List<PendingTrade> pendingTradesForTarget(String targetPlayerId) {
        return pendingTrades.stream()
                .filter(trade -> trade.isPending() && trade.targetPlayerId().equals(targetPlayerId))
                .toList();
    }

    public boolean hasPendingTradeInvolving(String playerId) {
        return pendingTrades.stream()
                .anyMatch(trade -> trade.isPending() && trade.involvesPlayer(playerId));
    }

    public void clearAllTradesForPlayer(String playerId) {
        pendingTrades.removeIf(trade -> trade.involvesPlayer(playerId));
    }

    public void addPendingAction(PendingAction action) {
        pendingActions.add(Objects.requireNonNull(action, "action"));
    }

    public void removePendingAction(PendingAction action) {
        pendingActions.remove(action);
    }

    public List<PendingAction> pendingActionsView() {
        return List.copyOf(pendingActions);
    }

    public Optional<PendingAction> findPendingAction(String actionId) {
        if (actionId == null || actionId.isBlank()) {
            return Optional.empty();
        }
        return pendingActions.stream()
                .filter(action -> action.actionId().equals(actionId))
                .findFirst();
    }

    public boolean hasPendingActions() {
        return !pendingActions.isEmpty();
    }

    public void clearAllActionsForPlayer(String playerId) {
        pendingActions.removeIf(action -> action.actorPlayerId().equals(playerId)
                || action.targetPlayerIds().contains(playerId));
    }
}
