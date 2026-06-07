package ie.ucd.bdic.group6.core.snapshot;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

public record SessionSnapshot(
        String sessionId,
        String roomCode,
        String hostPlayerId,
        int maxPlayers,
        String status,
        String activePlayerId,
        int actionsUsed,
        int actionsRemaining,
        String winnerPlayerId,
        int deckSize,
        int discardSize,
        List<PendingDebtSnapshot> pendingDebts,
        List<PendingTradeSnapshot> pendingTrades,
        List<PendingActionSnapshot> pendingActions,
        List<String> forfeitedPlayerIds,
        List<PlayerStateSnapshot> players,
        List<CardSnapshot> currentPlayerHand
) implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    public SessionSnapshot {
        sessionId = text(sessionId);
        roomCode = text(roomCode);
        hostPlayerId = text(hostPlayerId);
        status = text(status);
        activePlayerId = text(activePlayerId);
        winnerPlayerId = text(winnerPlayerId);
        pendingDebts = copy(pendingDebts);
        pendingTrades = copy(pendingTrades);
        pendingActions = copy(pendingActions);
        forfeitedPlayerIds = copy(forfeitedPlayerIds);
        players = copy(players);
        currentPlayerHand = copy(currentPlayerHand);
    }

    private static <T> List<T> copy(List<T> values) {
        return values == null ? List.of() : List.copyOf(values);
    }

    private static String text(String value) {
        return value == null ? "" : value;
    }
}
