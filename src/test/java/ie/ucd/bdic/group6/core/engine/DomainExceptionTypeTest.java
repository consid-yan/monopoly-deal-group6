package ie.ucd.bdic.group6.core.engine;

import ie.ucd.bdic.group6.command.CreateSessionCommand;
import ie.ucd.bdic.group6.command.JoinSessionCommand;
import ie.ucd.bdic.group6.command.PlayerDisconnectedCommand;
import ie.ucd.bdic.group6.command.StartGameCommand;
import ie.ucd.bdic.group6.core.snapshot.SessionSnapshot;
import ie.ucd.bdic.group6.exception.InvalidCommandException;
import ie.ucd.bdic.group6.exception.InvalidGameStateException;
import ie.ucd.bdic.group6.exception.PlayerNotFoundException;
import ie.ucd.bdic.group6.exception.SessionNotFoundException;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DomainExceptionTypeTest {

    @Test
    void missingSessionUsesSessionNotFoundException() {
        GameEngine engine = new GameEngine();

        SessionNotFoundException error = assertThrows(SessionNotFoundException.class,
                () -> engine.executeStartGame(new StartGameCommand(Map.of(
                        "sessionId", "missing-session",
                        "playerId", "host"
                ))));

        assertEquals("SESSION_NOT_FOUND", error.getErrorCode());
    }

    @Test
    void missingPlayerUsesPlayerNotFoundException() {
        GameEngine engine = new GameEngine();
        SessionSnapshot created = engine.executeCreateSession(new CreateSessionCommand(Map.of(
                "hostPlayerId", "host"
        )));

        PlayerNotFoundException error = assertThrows(PlayerNotFoundException.class,
                () -> engine.executePlayerDisconnected(new PlayerDisconnectedCommand(Map.of(
                        "sessionId", created.sessionId(),
                        "playerId", "missing-player"
                ))));

        assertEquals("PLAYER_NOT_FOUND", error.getErrorCode());
    }

    @Test
    void invalidCommandPayloadUsesInvalidCommandException() {
        GameEngine engine = new GameEngine();

        InvalidCommandException error = assertThrows(InvalidCommandException.class,
                () -> engine.executeCreateSession(new CreateSessionCommand(Map.of())));

        assertEquals("MISSING_REQUIRED_FIELD", error.getErrorCode());
    }

    @Test
    void invalidGameStateUsesInvalidGameStateException() {
        GameEngine engine = new GameEngine();
        SessionSnapshot created = engine.executeCreateSession(new CreateSessionCommand(Map.of(
                "hostPlayerId", "host"
        )));
        engine.executeJoinSession(new JoinSessionCommand(Map.of(
                "roomCode", created.roomCode(),
                "playerId", "player2"
        )));
        engine.executeStartGame(new StartGameCommand(Map.of(
                "sessionId", created.sessionId(),
                "playerId", "host"
        )));

        InvalidGameStateException error = assertThrows(InvalidGameStateException.class,
                () -> engine.executeJoinSession(new JoinSessionCommand(Map.of(
                        "roomCode", created.roomCode(),
                        "playerId", "late-player"
                ))));

        assertEquals("INVALID_SESSION_STATE", error.getErrorCode());
    }
}
