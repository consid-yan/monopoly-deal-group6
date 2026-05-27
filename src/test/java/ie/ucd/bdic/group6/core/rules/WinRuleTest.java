package ie.ucd.bdic.group6.core.rules;

import ie.ucd.bdic.group6.core.card.PropertyCard;
import ie.ucd.bdic.group6.core.player.Player;
import ie.ucd.bdic.group6.core.property.PropertyColor;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WinRuleTest {

    private final WinRule winRule = new WinRule();

    @Test
    void playerWithThreeCompleteSetsOnTheirTurnWins() {
        Player player = playerWithCompleteSets(PropertyColor.BROWN, PropertyColor.DARK_BLUE, PropertyColor.UTILITY);
        assertTrue(winRule.hasWon(player, true));
    }

    @Test
    void playerWithThreeCompleteSetsOnOpponentTurnDoesNotWin() {
        Player player = playerWithCompleteSets(PropertyColor.BROWN, PropertyColor.DARK_BLUE, PropertyColor.UTILITY);
        assertFalse(winRule.hasWon(player, false));
    }

    @Test
    void playerWithTwoCompleteSetsDoesNotWin() {
        Player player = playerWithCompleteSets(PropertyColor.BROWN, PropertyColor.DARK_BLUE);
        assertFalse(winRule.hasWon(player, true));
    }

    @Test
    void playerWithNoSetsDoesNotWin() {
        Player player = new Player("p1", "Player 1");
        assertFalse(winRule.hasWon(player, true));
    }

    private Player playerWithCompleteSets(PropertyColor... colors) {
        Player player = new Player("p1", "Player 1");
        int cardSeq = 0;
        for (PropertyColor color : colors) {
            int need = PropertyColor.fullSetSize(color);
            for (int i = 0; i < need; i++) {
                String id = "prop-" + (++cardSeq);
                PropertyCard card = new PropertyCard(id, color.name(), color, need);
                player.addToHand(card);
                if (i == 0) {
                    player.tryStartNewPropertyRowFromHand(color, card, Optional.empty());
                } else {
                    var row = player.propertyBoard().rowsView().stream()
                            .filter(r -> r.anchorColor() == color && r.canAdd(card, Optional.empty()))
                            .findFirst()
                            .orElseThrow();
                    player.tryPlayPropertyFromHand(row, card, Optional.empty());
                }
            }
        }
        return player;
    }
}
