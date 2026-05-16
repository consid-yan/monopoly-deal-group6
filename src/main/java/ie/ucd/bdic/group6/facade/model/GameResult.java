package ie.ucd.bdic.group6.facade.model;

import java.util.Map;
import java.util.Objects;

public class GameResult {
    private final boolean success;
    private final String code;
    private final String message;
    private final Map<String, Object> data;

    // 私有构造器（强制通过静态方法创建）
    private GameResult(boolean success, String code, String message, Map<String, Object> data) {
        this.success = success;
        this.code = Objects.requireNonNull(code, "code must not be null");
        this.message = Objects.requireNonNull(message, "message must not be null");
        this.data = data == null ? Map.of() : Map.copyOf(data);
    }

    // 成功结果（带数据）
    public static GameResult success(Map<String, Object> data) {
        return new GameResult(true, "SUCCESS", "Operation succeeded", data);
    }

    // 成功结果（无数据）
    public static GameResult success() {
        return success(Map.of());
    }

    // 失败结果（带错误码）
    public static GameResult failure(String code) {
        return new GameResult(false, code, "Operation failed", Map.of());
    }

    // 失败结果（带错误码和自定义消息）
    public static GameResult failure(String code, String message) {
        return new GameResult(false, code, message, Map.of());
    }

    // 未实现功能
    public static GameResult notImplemented(String feature) {
        Objects.requireNonNull(feature, "feature must not be null");
        return new GameResult(false, "NOT_IMPLEMENTED:" + feature,
                feature + " is not implemented yet", Map.of());
    }

    // Getter 方法（全部为不可变类型）
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
        return data; // 返回不可变Map，防止外部修改
    }
}