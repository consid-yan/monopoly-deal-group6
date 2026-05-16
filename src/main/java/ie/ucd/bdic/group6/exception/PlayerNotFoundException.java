package ie.ucd.bdic.group6.exception;

public class PlayerNotFoundException extends GameException {
    public PlayerNotFoundException(String message) {
        super("PLAYER_NOT_FOUND", message);
    }

    public PlayerNotFoundException(String message, Throwable cause) {
        super("PLAYER_NOT_FOUND", message, cause);
    }
}