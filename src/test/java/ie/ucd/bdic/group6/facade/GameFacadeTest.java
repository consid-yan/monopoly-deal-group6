package ie.ucd.bdic.group6.facade;

import ie.ucd.bdic.group6.command.CreateSessionCommand;
import ie.ucd.bdic.group6.command.JoinSessionCommand;
import ie.ucd.bdic.group6.command.StartGameCommand;
import ie.ucd.bdic.group6.core.snapshot.SessionSnapshot;
import ie.ucd.bdic.group6.facade.model.GameResult;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GameFacadeTest {

    @Test
    void defaultFacadeUsesOneSharedEngineAcrossHandlers() {
        GameFacade facade = new GameFacade();

        GameResult created = facade.execute(new CreateSessionCommand(Map.of(
                "hostPlayerId", "host",
                "maxPlayers", 5
        )));
        assertTrue(created.isSuccess());
        SessionSnapshot createdSnapshot = created.getSnapshot().orElseThrow();
        assertEquals("host", createdSnapshot.hostPlayerId());
        assertEquals(5, createdSnapshot.maxPlayers());

        GameResult joined = facade.execute(new JoinSessionCommand(Map.of(
                "roomCode", createdSnapshot.roomCode(),
                "playerId", "player2"
        )));
        assertTrue(joined.isSuccess());

        GameResult started = facade.execute(new StartGameCommand(Map.of(
                "sessionId", createdSnapshot.sessionId(),
                "playerId", "host"
        )));
        assertTrue(started.isSuccess());
        SessionSnapshot startedSnapshot = started.getSnapshot().orElseThrow();
        assertEquals("ACTIVE", startedSnapshot.status());
        assertEquals("host", startedSnapshot.activePlayerId());
    }

    @Test
    void nullCommandReturnsFailure() {
        GameFacade facade = new GameFacade();
        GameResult result = facade.execute(null);
        assertFalse(result.isSuccess());
        assertEquals("COMMAND_REQUIRED", result.getCode());
    }

    @Test
    void gameExceptionIsWrappedIntoFailureResult() {
        GameFacade facade = new GameFacade();
        GameResult result = facade.execute(new StartGameCommand(Map.of(
                "sessionId", "nonexistent-session",
                "playerId", "host"
        )));
        assertFalse(result.isSuccess());
        assertEquals("SESSION_NOT_FOUND", result.getCode());
    }

    @Test
    void missingRequiredFieldIsWrappedIntoFailureResult() {
        GameFacade facade = new GameFacade();
        GameResult result = facade.execute(new CreateSessionCommand(Map.of()));
        assertFalse(result.isSuccess());
        assertEquals("MISSING_REQUIRED_FIELD", result.getCode());
    }

    @Test
    void joinNonexistentSessionReturnsFailure() {
        GameFacade facade = new GameFacade();
        GameResult result = facade.execute(new JoinSessionCommand(Map.of(
                "roomCode", "ZZZZZZ",
                "playerId", "player1"
        )));
        assertFalse(result.isSuccess());
        assertEquals("SESSION_NOT_FOUND", result.getCode());
    }
}
