package ie.ucd.bdic.group6.network.message;

import java.io.Serial;
import java.util.Map;

public record ServerUpdate(Map<String, Object> snapshot) implements Message {
    @Serial
    private static final long serialVersionUID = 1L;

    public ServerUpdate {
        snapshot = NetworkPayloads.normalizeMap(snapshot);
    }
}
