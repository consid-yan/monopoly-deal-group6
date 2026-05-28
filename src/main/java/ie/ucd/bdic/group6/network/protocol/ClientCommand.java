package ie.ucd.bdic.group6.network.protocol;

import ie.ucd.bdic.group6.command.CommandType;

import java.io.Serial;
import java.io.Serializable;
import java.util.Map;
import java.util.Objects;

public record ClientCommand(
        String requestId,
        CommandType commandType,
        Map<String, Object> payload) implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    public ClientCommand {
        requestId = Objects.requireNonNull(requestId, "requestId");
        Objects.requireNonNull(commandType, "commandType");
        payload = NetworkPayloads.normalizeMap(payload);
    }
}
