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
    private final List<PendingDebt> pendingDebts = new ArrayList<>();
    private final List<PendingTrade> pendingTrades = new ArrayList<>();
    private final List<PendingAction> pendingActions = new ArrayList<>();
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

    public boolean reshuffleDiscardIntoDraw() {
        if (!drawPile.isEmpty() || discardPile.isEmpty()) {
            return false;
        }
        java.util.Collections.shuffle(discardPile);
        for (int i = discardPile.size() - 1; i >= 0; i--) {
            drawPile.discardToBottom(discardPile.get(i));
        }
        discardPile.clear();
        return true;
    }

    public Optional<String> winnerPlayerId() {
        return Optional.ofNullable(winnerPlayerId);
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

    public boolean hasPendingDebtForPlayer(String playerId) {
        return pendingDebts.stream()
                .anyMatch(debt -> debt.isPending() && debt.debtorPlayerId().equals(playerId));
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

    public List<PendingTrade> pendingTradesForProposer(String proposerPlayerId) {
        return pendingTrades.stream()
                .filter(trade -> trade.isPending() && trade.proposerPlayerId().equals(proposerPlayerId))
                .toList();
    }

    public List<PendingTrade> pendingTradesForTarget(String targetPlayerId) {
        return pendingTrades.stream()
                .filter(trade -> trade.isPending() && trade.targetPlayerId().equals(targetPlayerId))
                .toList();
    }

    public List<PendingTrade> pendingTradesInvolving(String playerId) {
        return pendingTrades.stream()
                .filter(trade -> trade.isPending() && trade.involvesPlayer(playerId))
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
