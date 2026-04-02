package ie.ucd.bdic.group6.facade.model;

public class GameResult {
    private final boolean success;
    private final String message;

    public GameResult(boolean success, String message) {
        this.success = success;
        this.message = message;
    }

    public static GameResult empty() {
        return notImplemented("COMMAND");
    }

    public static GameResult success(String message) {
        return new GameResult(true, message);
    }

    public static GameResult failure(String message) {
        return new GameResult(false, message);
    }

    public static GameResult notImplemented(String commandName) {
        return new GameResult(true, commandName + "_NOT_IMPLEMENTED");
    }

    public boolean isSuccess() {
        return success;
    }

    public String getMessage() {
        return message;
    }
}
