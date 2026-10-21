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

    /**
     * [Gameplay Step A: Record an Action]
     * Called after each card play to consume one of the 3 available actions for the current turn.
     * When actionsUsed reaches MAX_ACTIONS_PER_TURN (3), the player can no longer play cards
     * and should end their turn (or will be forced into end-turn flow).
     */
    public void recordAction() {
        if (!canRecordAction()) {
            throw new IllegalStateException("turn action limit has already been reached");
        }
        actionsUsed++;
    }

    /**
     * [Gameplay Step E: Advance to Next Turn]
     * Moves the turn to the next player in join order using round-robin rotation:
     * currentIndex = (currentIndex + 1) % turnOrder.size()
     * Resets actionsUsed back to 0 for the new player's turn.
     * Returns the playerId of the new active player.
     */
    public String advanceToNextTurn() {
        currentIndex = (currentIndex + 1) % turnOrder.size();
        actionsUsed = 0;
        return activePlayerId();
    }
}
