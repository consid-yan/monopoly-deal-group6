package ie.ucd.bdic.group6.app;

import ie.ucd.bdic.group6.network.message.ServerResponse;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GameSessionCoordinatorTest {

    @Test
    void rejoinWithoutSavedCredentialsCompletesFutureExceptionally() {
        try(GameSessionCoordinator coordinator = new GameSessionCoordinator(new AppConfig())){

        CompletableFuture<ServerResponse> future = assertDoesNotThrow(() ->
                coordinator.rejoinSession("ABC123"));

        assertTrue(future.isCompletedExceptionally());
        CompletionException error = assertInstanceOf(CompletionException.class,
                assertThrowsFromJoin(future));
        assertInstanceOf(IllegalArgumentException.class, error.getCause());
        }
    }

    private Throwable assertThrowsFromJoin(CompletableFuture<?> future) {
        try {
            future.join();
        } catch (Throwable throwable) {
            return throwable;
        }
        throw new AssertionError("Expected future.join() to throw");
    }
}
