package ie.ucd.bdic.group6.network.protocol;

import java.io.Serial;
import java.io.Serializable;
import java.util.Map;
import java.util.Objects;

public record ServerResult(
        String requestId,
        boolean success,
        String code,
        String message,
        Map<String, Object> data) implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    public ServerResult {
        requestId = Objects.requireNonNull(requestId, "requestId");
        code = Objects.requireNonNullElse(code, "");
        message = Objects.requireNonNullElse(message, "");
        data = NetworkPayloads.normalizeMap(data);
    }
}
