package ie.ucd.bdic.group6.exception;

public class InvalidCommandException extends GameException {
    public InvalidCommandException(String message) {
        super("INVALID_COMMAND", message);
    }

    public InvalidCommandException(String message, Throwable cause) {
        super("INVALID_COMMAND", message, cause);
    }
}