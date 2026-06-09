package ie.ucd.bdic.group6.command;

import ie.ucd.bdic.group6.command.card.DiscardCardCommand;
import ie.ucd.bdic.group6.command.game.EndTurnCommand;
import ie.ucd.bdic.group6.command.card.BankCardCommand;
import ie.ucd.bdic.group6.command.card.PlayActionCardCommand;
import ie.ucd.bdic.group6.command.card.PlayPropertyCardCommand;
import ie.ucd.bdic.group6.command.action.RespondActionCommand;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GameplayCommandTest {

    @Test
    void typedPlayCommandsBuildNetworkPayloads() {
        BankCardCommand bank = new BankCardCommand("session", "player", "money");
        PlayPropertyCardCommand property = new PlayPropertyCardCommand(
                "session", "player", "wild", "BROWN");
        PlayActionCardCommand action = PlayActionCardCommand.builder("session", "player", "rent")
                .targetPlayerId("target")
                .propertyColor("BROWN")
                .doubleRentCardId("double")
                .build();

        assertEquals("BANK", bank.getPlayMode());
        assertEquals("money", bank.getCardId());
        assertEquals("PROPERTY", property.getPlayMode());
        assertEquals("BROWN", property.getPropertyColor());
        assertEquals("ACTION", action.getPlayMode());
        assertEquals("target", action.getTargetPlayerId());
        assertEquals("double", action.getDoubleRentCardId());
    }

    @Test
    void typedTurnAndResponseCommandsBuildNetworkPayloads() {
        DiscardCardCommand discard = new DiscardCardCommand("session", "player", List.of("card"));
        EndTurnCommand endTurn = new EndTurnCommand("session", "player");
        RespondActionCommand response = new RespondActionCommand(
                "session", "player", "action", false, "just-say-no");

        assertEquals(List.of("card"), discard.getDiscardedCardIds());
        assertEquals("session", endTurn.getSessionId());
        assertEquals("player", endTurn.getPlayerId());
        assertEquals("action", response.getActionId());
        assertEquals("just-say-no", response.getJustSayNoCardId());
    }
}
