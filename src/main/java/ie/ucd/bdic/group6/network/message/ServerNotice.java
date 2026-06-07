package ie.ucd.bdic.group6.network.message;

import java.io.Serial;
import java.util.Map;
import java.util.Objects;

public record ServerNotice(NoticeType type, Map<String, Object> payload) implements Message {
    @Serial
    private static final long serialVersionUID = 1L;

    public ServerNotice {
        Objects.requireNonNull(type, "type");
        payload = NetworkPayloads.normalizeMap(payload);
    }
}
