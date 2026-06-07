package ie.ucd.bdic.group6.core.snapshot;

import java.io.Serial;
import java.io.Serializable;

public record PendingDebtSnapshot(
        String debtId,
        String creditorPlayerId,
        String debtorPlayerId,
        int amountM,
        String sourceAction,
        String sourceCardId
) implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    public PendingDebtSnapshot {
        debtId = text(debtId);
        creditorPlayerId = text(creditorPlayerId);
        debtorPlayerId = text(debtorPlayerId);
        sourceAction = text(sourceAction);
        sourceCardId = text(sourceCardId);
    }

    private static String text(String value) {
        return value == null ? "" : value;
    }
}
