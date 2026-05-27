package ie.ucd.bdic.group6.core.card;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DrawPileRulesTest {

    @Test
    void reshuffleMovesDiscardIntoEmptyDrawPile() {
        Deck draw = new Deck(List.of());
        List<Card> discard = new ArrayList<>();
        discard.add(new MoneyCard("m1", "1M", 1));
        discard.add(new MoneyCard("m2", "2M", 2));
        var result = DrawPileRules.reshuffleDiscardIntoDrawPile(draw, discard, new Random(42));
        assertTrue(result.isSuccess());
        assertEquals(2, result.value().orElseThrow());
        assertTrue(discard.isEmpty());
        assertEquals(2, draw.size());
    }
}
