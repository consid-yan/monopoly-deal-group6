package ie.ucd.bdic.group6.core.engine;

import java.util.List;
import java.util.Objects;

public final class TurnManager {
    public static final int MAX_ACTIONS_PER_TURN = 3;

    private final List<String> turnOrder;
    private int currentIndex;
    private int actionsUsed;

    public TurnManager(List<String> turnOrder) {
        Objects.requireNonNull(turnOrder, "turnOrder");
        if (turnOrder.isEmpty()) {
            throw new IllegalArgumentException("turnOrder must not be empty");
        }
        this.turnOrder = List.copyOf(turnOrder);
    }

    public List<String> turnOrder() {
        return turnOrder;
    }

    public String activePlayerId() {
        return turnOrder.get(currentIndex);
    }

    public boolean isActivePlayer(String playerId) {
        return activePlayerId().equals(playerId);
    }

    public int actionsUsed() {
        return actionsUsed;
    }

    public int actionsRemaining() {
        return Math.max(0, MAX_ACTIONS_PER_TURN - actionsUsed);
    }

    public boolean canRecordAction() {
        return actionsUsed < MAX_ACTIONS_PER_TURN;
    }

    public void recordAction() {
        if (!canRecordAction()) {
            throw new IllegalStateException("turn action limit has already been reached");
        }
        actionsUsed++;
    }

    public String advanceToNextTurn() {
        currentIndex = (currentIndex + 1) % turnOrder.size();
        actionsUsed = 0;
        return activePlayerId();
    }
}
