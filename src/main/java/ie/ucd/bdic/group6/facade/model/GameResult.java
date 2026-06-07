package ie.ucd.bdic.group6.facade.model;

import ie.ucd.bdic.group6.core.snapshot.SessionSnapshot;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public class GameResult {
    private final boolean success;
    private final String code;
    private final String message;
    private final Map<String, Object> data;
    private final SessionSnapshot snapshot;

    private GameResult(
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

    public static GameResult success(Map<String, Object> data) {
        return new GameResult(true, "SUCCESS", "Operation succeeded", data, null);
    }

    public static GameResult success(SessionSnapshot snapshot) {
        return new GameResult(
                true,
                "SUCCESS",
                "Operation succeeded",
                Map.of(),
                Objects.requireNonNull(snapshot, "snapshot must not be null")
        );
    }

    public static GameResult success() {
        return success(Map.of());
    }

    public static GameResult failure(String code, String message) {
        return new GameResult(false, code, message, Map.of(), null);
    }

    public static GameResult notImplemented(String feature) {
        Objects.requireNonNull(feature, "feature must not be null");
        return new GameResult(false, "NOT_IMPLEMENTED:" + feature,
                feature + " is not implemented yet", Map.of(), null);
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
