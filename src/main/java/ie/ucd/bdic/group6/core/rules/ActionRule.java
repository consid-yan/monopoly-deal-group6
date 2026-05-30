package ie.ucd.bdic.group6.core.rules;

import ie.ucd.bdic.group6.core.engine.GameSession;
import ie.ucd.bdic.group6.core.engine.SessionStatus;
import ie.ucd.bdic.group6.core.engine.TurnManager;
import ie.ucd.bdic.group6.exception.InvalidGameStateException;

public class ActionRule {
    // TODO(engine-action): Add card-specific and target-specific validation before advanced action cards are enabled.

    public void requireLobby(GameSession session) {
        if (session.status() != SessionStatus.LOBBY) {
            throw new InvalidGameStateException("INVALID_SESSION_STATE", "This command is only allowed in the lobby");
        }
    }

    public void requireActive(GameSession session) {
        if (session.status() != SessionStatus.ACTIVE) {
            throw new InvalidGameStateException("INVALID_SESSION_STATE", "This command requires an active game");
        }
    }

    public void requireActivePlayer(TurnManager turnManager, String playerId) {
        if (!turnManager.isActivePlayer(playerId)) {
            throw new InvalidGameStateException("NOT_ACTIVE_PLAYER", "It is not this player's turn");
        }
    }

    public void requireActionAvailable(TurnManager turnManager) {
        if (!turnManager.canRecordAction()) {
            throw new InvalidGameStateException("ACTION_LIMIT_REACHED", "A player can play at most 3 cards per turn");
        }
    }
}
