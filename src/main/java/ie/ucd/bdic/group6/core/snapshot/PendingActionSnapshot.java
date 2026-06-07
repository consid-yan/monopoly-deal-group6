package ie.ucd.bdic.group6.core.snapshot;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;
import java.util.Map;

public record PendingActionSnapshot(
        String actionId,
        String actorPlayerId,
        String actionType,
        String actionCardId,
        String doubleRentCardId,
        String propertyColor,
        int amountM,
        String targetCardId,
        String targetPropertyRowId,
        String offeredCardId,
        String requestedCardId,
        List<String> targetPlayerIds,
        Map<String, String> responses,
        String createdAt
) implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    public PendingActionSnapshot {
        actionId = text(actionId);
        actorPlayerId = text(actorPlayerId);
        actionType = text(actionType);
        actionCardId = text(actionCardId);
        doubleRentCardId = text(doubleRentCardId);
        propertyColor = text(propertyColor);
        targetCardId = text(targetCardId);
        targetPropertyRowId = text(targetPropertyRowId);
        offeredCardId = text(offeredCardId);
        requestedCardId = text(requestedCardId);
        targetPlayerIds = targetPlayerIds == null ? List.of() : List.copyOf(targetPlayerIds);
        responses = responses == null ? Map.of() : Map.copyOf(responses);
        createdAt = text(createdAt);
    }

    private static String text(String value) {
        return value == null ? "" : value;
    }
}
