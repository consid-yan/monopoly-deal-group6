package ie.ucd.bdic.group6.core.snapshot;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

public record PlayerStateSnapshot(
        String playerId,
        String name,
        String playerStatus,
        String playerPhase,
        boolean connected,
        String disconnectedAt,
        boolean defeated,
        int handCount,
        int bankTotalValueM,
        List<CardSnapshot> bankCards,
        List<PropertyRowSnapshot> propertyRows
) implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    public PlayerStateSnapshot {
        playerId = text(playerId);
        name = text(name);
        playerStatus = text(playerStatus);
        playerPhase = text(playerPhase);
        disconnectedAt = text(disconnectedAt);
        bankCards = copy(bankCards);
        propertyRows = copy(propertyRows);
    }

    public PlayerStateSnapshot(
            String playerId,
            String name,
            String playerStatus,
            boolean connected,
            String disconnectedAt,
            boolean defeated,
            int handCount,
            int bankTotalValueM,
            List<CardSnapshot> bankCards,
            List<PropertyRowSnapshot> propertyRows) {
        this(
                playerId,
                name,
                playerStatus,
                "WAITING",
                connected,
                disconnectedAt,
                defeated,
                handCount,
                bankTotalValueM,
                bankCards,
                propertyRows);
    }

    private static <T> List<T> copy(List<T> values) {
        return values == null ? List.of() : List.copyOf(values);
    }

    private static String text(String value) {
        return value == null ? "" : value;
    }
}
