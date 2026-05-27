package ie.ucd.bdic.group6.core.common;

/**
 * Small helpers for callers (engine/UI) consuming {@link OperationResult}.
 */
public final class OperationResults {

    private OperationResults() {
    }

    public static <T> T requireSuccess(OperationResult<T> result) {
        if (result.isSuccess()) {
            return result.value().orElse(null);
        }
        OperationError code = result.errorCode().orElse(OperationError.GENERIC);
        String message = result.errorMessage().orElse("");
        throw new IllegalStateException(code + ": " + message);
    }
}
