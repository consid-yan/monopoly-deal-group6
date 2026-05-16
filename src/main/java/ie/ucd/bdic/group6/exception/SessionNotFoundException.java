package ie.ucd.bdic.group6.exception;

public class SessionNotFoundException extends GameException {
    public SessionNotFoundException(String message) {
        super("SESSION_NOT_FOUND", message);
    }

    public SessionNotFoundException(String message, Throwable cause) {
        super("SESSION_NOT_FOUND", message, cause);
    }
}