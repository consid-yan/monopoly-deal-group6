package ie.ucd.bdic.group6.network.message;

import ie.ucd.bdic.group6.command.CommandType;

import java.io.Serial;
import java.util.Map;
import java.util.Objects;

public record ClientRequest(String requestId, CommandType commandType, Map<String, Object> payload)
        implements Message {
    @Serial
    private static final long serialVersionUID = 1L;

    public ClientRequest {
        requestId = Objects.requireNonNull(requestId, "requestId");
        Objects.requireNonNull(commandType, "commandType");
        payload = NetworkPayloads.normalizeMap(payload);
    }
}
