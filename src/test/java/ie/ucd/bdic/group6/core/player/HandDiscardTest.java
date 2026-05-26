package ie.ucd.bdic.group6.core.player;

import ie.ucd.bdic.group6.core.card.Card;
import ie.ucd.bdic.group6.core.card.MoneyCard;
import ie.ucd.bdic.group6.core.common.OperationResult;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HandDiscardTest {

    @Test
    void discardDownToSendsCardsToDiscardSink() {
        Hand hand = new Hand();
        MoneyCard keep = new MoneyCard("keep", "1M", 1);
        MoneyCard drop = new MoneyCard("drop", "2M", 2);
        hand.add(keep);
        hand.add(drop);

        List<Card> discarded = new ArrayList<>();
        CardDiscardSink sink = discarded::add;

        OperationResult<Void> result = hand.discardDownTo(1, List.of(drop), sink);
        assertTrue(result.isSuccess());
        assertEquals(1, hand.size());
        assertEquals(1, discarded.size());
        assertEquals("drop", discarded.getFirst().id());
    }
}
