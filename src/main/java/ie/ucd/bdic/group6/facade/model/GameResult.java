package ie.ucd.bdic.group6.facade.model;

import java.util.Map;
import java.util.Objects;

public class GameResult {
    private final boolean success;
    private final String code;
    private final String message;
    private final Map<String, Object> data;

    private GameResult(boolean success, String code, String message, Map<String, Object> data) {
        this.success = success;
        this.code = Objects.requireNonNull(code, "code must not be null");
        this.message = Objects.requireNonNull(message, "message must not be null");
        this.data = data == null ? Map.of() : Map.copyOf(data);
    }

    public static GameResult success(Map<String, Object> data) {
        return new GameResult(true, "SUCCESS", "Operation succeeded", data);
    }

    public static GameResult success() {
        return success(Map.of());
    }

    public static GameResult failure(String code, String message) {
        return new GameResult(false, code, message, Map.of());
    }

    public static GameResult notImplemented(String feature) {
        Objects.requireNonNull(feature, "feature must not be null");
        return new GameResult(false, "NOT_IMPLEMENTED:" + feature,
                feature + " is not implemented yet", Map.of());
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
}
