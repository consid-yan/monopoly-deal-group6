package ie.ucd.bdic.group6.network.message;

import java.io.Serial;
import java.util.Map;
import java.util.Objects;

public record ServerResponse(
        String requestId,
        boolean accepted,
        String code,
        String message,
        Map<String, Object> payload
) implements Message {
    @Serial
    private static final long serialVersionUID = 1L;

    public ServerResponse {
        requestId = Objects.requireNonNull(requestId, "requestId");
        code = code == null ? "" : code;
        message = message == null ? "" : message;
        payload = NetworkPayloads.normalizeMap(payload);
    }
}
