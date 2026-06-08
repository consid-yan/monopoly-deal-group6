package ie.ucd.bdic.group6.facade;

import ie.ucd.bdic.group6.core.player.Player;
import ie.ucd.bdic.group6.core.player.PlayerRollbackSnapshot;

/**
 * Facade over {@link Player} table zones (hand, bank, property board) for engine/UI integration.
 * Command-level orchestration remains in {@link GameFacade}; this class exposes stable player-table operations.
 */
public record PlayerTableFacade(Player player) {

    public PlayerRollbackSnapshot snapshot() {
        return player.snapshot();
    }

    public void restore(PlayerRollbackSnapshot snap) {
        player.restore(snap);
    }
}
