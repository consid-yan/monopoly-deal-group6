package ie.ucd.bdic.group6.core.rules;

import ie.ucd.bdic.group6.core.player.Player;

public class WinRule {
    public boolean hasWon(Player player) {
        return player.canDeclareVictory();
    }
}
