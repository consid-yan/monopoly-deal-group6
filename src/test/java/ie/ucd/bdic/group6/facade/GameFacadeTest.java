package ie.ucd.bdic.group6.facade;

import ie.ucd.bdic.group6.command.CreateSessionCommand;
import ie.ucd.bdic.group6.command.JoinSessionCommand;
import ie.ucd.bdic.group6.command.StartGameCommand;
import ie.ucd.bdic.group6.facade.model.GameResult;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
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

        GameResult joined = facade.execute(new JoinSessionCommand(Map.of(
                "sessionCode", created.getData().get("sessionCode"),
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
}
