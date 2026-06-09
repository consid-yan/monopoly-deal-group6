package ie.ucd.bdic.group6.core.engine;

public final class PlayerPhaseResolver {
    private PlayerPhaseResolver() {
    }

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
