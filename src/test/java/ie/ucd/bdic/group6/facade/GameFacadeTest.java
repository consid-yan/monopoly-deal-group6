package ie.ucd.bdic.group6.facade;

import ie.ucd.bdic.group6.command.CreateSessionCommand;
import ie.ucd.bdic.group6.command.JoinSessionCommand;
import ie.ucd.bdic.group6.command.StartGameCommand;
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
        assertEquals("host", created.getData().get("hostPlayerId"));
        assertEquals(5, created.getData().get("maxPlayers"));

        GameResult joined = facade.execute(new JoinSessionCommand(Map.of(
                "roomCode", created.getData().get("roomCode"),
                "playerId", "player2"
        )));
        assertTrue(joined.isSuccess());

        GameResult started = facade.execute(new StartGameCommand(Map.of(
                "sessionId", created.getData().get("sessionId"),
                "playerId", "host"
        )));
        assertTrue(started.isSuccess());
        assertEquals("ACTIVE", started.getData().get("status"));
        assertEquals("host", started.getData().get("activePlayerId"));
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
