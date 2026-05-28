package ie.ucd.bdic.group6.network.protocol;

import java.io.Serial;
import java.io.Serializable;
import java.util.Map;
import java.util.Objects;

public record StateUpdate(
        String sessionId,
        String playerId,
        Map<String, Object> data) implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    public StateUpdate {
        sessionId = Objects.requireNonNull(sessionId, "sessionId");
        playerId = Objects.requireNonNull(playerId, "playerId");
        data = NetworkPayloads.normalizeMap(data);
    }
}
