package ie.ucd.bdic.group6.core.rules;

import ie.ucd.bdic.group6.core.player.Player;

public class WinRule {
    /**
     * [Gameplay Step 6: Check Win Condition]
     * A player wins by completing 3 or more property sets of different colors.
     * A "complete set" means having the required number of property cards in one row
     * (e.g., 3 red cards, 4 railroad cards, 2 dark blue cards).
     * Checked after every turn-end and after every action that could change property state.
     */
    public boolean hasWon(Player player) {
        return player.canDeclareVictory();
    }
}
