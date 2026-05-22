package ie.ucd.bdic.group6.command;

import java.util.Map;
import java.util.Objects;

public abstract class AbstractCommand implements Command {
    private final Map<String, Object> payload;

    protected AbstractCommand(Map<String, Object> payload) {
        this.payload = Objects.requireNonNullElse(payload, Map.of());
    }

    @Override
    public Map<String, Object> payload() {
        return payload;
    }

    // Common parameter accessors.
    public String getSessionId() {
        return (String) payload.get("sessionId");
    }

    public String getPlayerId() {
        return (String) payload.get("playerId");
    }
}
