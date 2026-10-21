package ie.ucd.bdic.group6.core.engine;

public final class PlayerPhaseResolver {
    private PlayerPhaseResolver() {
    }

    /**
     * [Gameplay Step P: Resolve Player Phase]
     * Determines what a player can do right now based on the full game state:
     *   WAITING    — not your turn, or waiting for others to respond
     *   ACTING     — your turn, you can play cards (up to 3 actions)
     *   RESPONDING — you must answer a pending action/debt/trade before anything else
     *   DISCARDING — your turn is ending but you have >7 cards, must discard first
     * This is the single source of truth that both the engine and the UI use
     * to decide which controls are available.
     */
    public static PlayerPhase resolve(GameSession session, String playerId) {
        if (session == null
                || playerId == null
                || playerId.isBlank()
                || session.status() != SessionStatus.ACTIVE
                || session.isDefeated(playerId)
                || session.activeGame().isEmpty()) {
            return PlayerPhase.WAITING;
        }

        ActiveGameState activeGame = session.activeGame().orElseThrow();
        boolean mustRespond = activeGame.pendingActionsView().stream()
                .anyMatch(action -> action.awaitsResponseFrom(playerId))
                || !activeGame.pendingDebtsForDebtor(playerId).isEmpty()
                || !activeGame.pendingTradesForTarget(playerId).isEmpty();
        if (mustRespond) {
            return PlayerPhase.RESPONDING;
        }
        if (activeGame.hasPendingActions() || !activeGame.pendingDebtsView().isEmpty()) {
            return PlayerPhase.WAITING;
        }
        if (!activeGame.turnManager().isActivePlayer(playerId)) {
            return PlayerPhase.WAITING;
        }
        if (!activeGame.pendingTradesForTarget(playerId).isEmpty()) {
            return PlayerPhase.RESPONDING;
        }

        int handSize = session.findPlayer(playerId).orElseThrow().hand().size();
        boolean turnEnding = activeGame.endTurnRequested()
                || activeGame.turnManager().actionsRemaining() == 0;
        if (turnEnding && handSize > 7) {
            return PlayerPhase.DISCARDING;
        }
        if (turnEnding) {
            return PlayerPhase.WAITING;
        }
        return PlayerPhase.ACTING;
    }
}
