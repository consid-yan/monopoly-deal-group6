package ie.ucd.bdic.group6.core.card;

public record ActionExecutionResult(boolean success, String message) {
    public static ActionExecutionResult success(String message) {
        return new ActionExecutionResult(true, message);
    }

    public static ActionExecutionResult failure(String message) {
        return new ActionExecutionResult(false, message);
    }
}

