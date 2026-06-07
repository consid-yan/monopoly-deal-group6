package ie.ucd.bdic.group6.network.message;

import ie.ucd.bdic.group6.core.snapshot.SessionSnapshot;

import java.io.Serial;
import java.util.Map;
import java.util.Objects;

public record ServerResponse(
        String requestId,
        boolean accepted,
        String code,
        String message,
        Map<String, Object> payload,
        SessionSnapshot snapshot
) implements Message {
    @Serial
    private static final long serialVersionUID = 1L;

    public ServerResponse {
        requestId = Objects.requireNonNull(requestId, "requestId");
        code = Objects.requireNonNull(code, "code");
        message = Objects.requireNonNull(message, "message");
        payload = NetworkPayloads.normalizeMap(payload);
    }
}
