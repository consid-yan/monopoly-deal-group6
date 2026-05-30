package ie.ucd.bdic.group6.exception;

public class InvalidGameStateException extends GameException {
    public InvalidGameStateException(String message) {
        super("INVALID_GAME_STATE", message);
    }

    public InvalidGameStateException(String errorCode, String message) {
        super(errorCode, message);
    }

    public InvalidGameStateException(String message, Throwable cause) {
        super("INVALID_GAME_STATE", message, cause);
    }

    public InvalidGameStateException(String errorCode, String message, Throwable cause) {
        super(errorCode, message, cause);
    }
}
