package ie.ucd.bdic.group6.network.protocol;

import java.io.Serial;
import java.io.Serializable;
import java.util.Map;
import java.util.Objects;

public record ServerNotice(
        String type,
        String message,
        Map<String, Object> data) implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    public ServerNotice {
        type = Objects.requireNonNullElse(type, "");
        message = Objects.requireNonNullElse(message, "");
        data = NetworkPayloads.normalizeMap(data);
    }
}
