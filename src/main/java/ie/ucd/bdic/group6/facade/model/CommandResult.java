package ie.ucd.bdic.group6.facade.model;

import ie.ucd.bdic.group6.core.snapshot.SessionSnapshot;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public class CommandResult {
    private final boolean success;
    private final String code;
    private final String message;
    private final Map<String, Object> data;
    private final SessionSnapshot snapshot;

    private CommandResult(
            boolean success,
            String code,
            String message,
            Map<String, Object> data,
            SessionSnapshot snapshot) {
        this.success = success;
        this.code = Objects.requireNonNull(code, "code must not be null");
        this.message = Objects.requireNonNull(message, "message must not be null");
        this.data = data == null ? Map.of() : Map.copyOf(data);
        this.snapshot = snapshot;
    }

    public static CommandResult success(Map<String, Object> data) {
        return new CommandResult(true, "SUCCESS", "Operation succeeded", data, null);
    }

    public static CommandResult success(SessionSnapshot snapshot) {
        return new CommandResult(
                true,
                "SUCCESS",
                "Operation succeeded",
                Map.of(),
                Objects.requireNonNull(snapshot, "snapshot must not be null")
        );
    }

    public static CommandResult success() {
        return success(Map.of());
    }

    public static CommandResult failure(String code, String message) {
        return new CommandResult(false, code, message, Map.of(), null);
    }

    public boolean isSuccess() {
        return success;
    }

    public String getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }

    public Map<String, Object> getData() {
        return data;
    }

    public Optional<SessionSnapshot> getSnapshot() {
        return Optional.ofNullable(snapshot);
    }
}
