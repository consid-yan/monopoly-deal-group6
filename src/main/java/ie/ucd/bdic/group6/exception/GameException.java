package ie.ucd.bdic.group6.exception;

import java.util.Objects;

public class GameException extends RuntimeException {
    private final String errorCode;

    public GameException(String errorCode, String message) {
        super(Objects.requireNonNull(message, "message must not be null"));
        this.errorCode = Objects.requireNonNull(errorCode, "errorCode must not be null");
    }

    public GameException(String errorCode, String message, Throwable cause) {
        super(Objects.requireNonNull(message, "message must not be null"),
                Objects.requireNonNull(cause, "cause must not be null"));
        this.errorCode = Objects.requireNonNull(errorCode, "errorCode must not be null");
    }

    public String getErrorCode() {
        return errorCode;
    }
}