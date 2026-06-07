package ie.ucd.bdic.group6.core.snapshot;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

public record PendingTradeSnapshot(
        String tradeId,
        String proposerPlayerId,
        String targetPlayerId,
        List<String> offeredCardIds,
        List<String> requestedCardIds,
        String status,
        String createdAt,
        String respondedAt
) implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    public PendingTradeSnapshot {
        tradeId = text(tradeId);
        proposerPlayerId = text(proposerPlayerId);
        targetPlayerId = text(targetPlayerId);
        offeredCardIds = copy(offeredCardIds);
        requestedCardIds = copy(requestedCardIds);
        status = text(status);
        createdAt = text(createdAt);
        respondedAt = text(respondedAt);
    }

    private static List<String> copy(List<String> values) {
        return values == null ? List.of() : List.copyOf(values);
    }

    private static String text(String value) {
        return value == null ? "" : value;
    }
}
