package ie.ucd.bdic.group6.core.rules;

import ie.ucd.bdic.group6.core.player.Player;

public class WinRule {
    public boolean hasWon(Player player, boolean isPlayerTurn) {
        return player.canDeclareVictory(isPlayerTurn);
    }
}
