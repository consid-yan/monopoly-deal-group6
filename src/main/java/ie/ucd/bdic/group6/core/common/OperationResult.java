package ie.ucd.bdic.group6.core.common;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

/**
 * Explicit success/failure for domain operations (preferred over bare {@code boolean} for UI/engine feedback).
 *
 * @param <T> value carried on success
 */
public sealed interface OperationResult<T> permits OperationResult.Success, OperationResult.Failure {

    static <T> OperationResult<T> ok(T value) {
        return new Success<>(value);
    }

    static OperationResult<Void> ok() {
        return new Success<>(null);
    }

    static <T> OperationResult<T> fail(OperationError code, String message) {
        return new Failure<>(Objects.requireNonNull(code, "code"), message == null ? "" : message);
    }

    boolean isSuccess();

    Optional<T> value();

    Optional<OperationError> errorCode();

    Optional<String> errorMessage();

    default <U> OperationResult<U> map(Function<? super T, ? extends U> mapper) {
        if (isSuccess()) {
            return ok(mapper.apply(value().orElse(null)));
        }
        return fail(errorCode().orElse(OperationError.GENERIC), errorMessage().orElse(""));
    }

    record Success<T>(T payload) implements OperationResult<T> {
        @Override
        public boolean isSuccess() {
            return true;
        }

        @Override
        public Optional<T> value() {
            return Optional.ofNullable(payload);
        }

        @Override
        public Optional<OperationError> errorCode() {
            return Optional.empty();
        }

        @Override
        public Optional<String> errorMessage() {
            return Optional.empty();
        }
    }

    record Failure<T>(OperationError code, String message) implements OperationResult<T> {
        public Failure {
            Objects.requireNonNull(code, "code");
            message = message == null ? "" : message;
        }

        @Override
        public boolean isSuccess() {
            return false;
        }

        @Override
        public Optional<T> value() {
            return Optional.empty();
        }

        @Override
        public Optional<OperationError> errorCode() {
            return Optional.of(code);
        }

        @Override
        public Optional<String> errorMessage() {
            return Optional.of(message);
        }
    }
}
