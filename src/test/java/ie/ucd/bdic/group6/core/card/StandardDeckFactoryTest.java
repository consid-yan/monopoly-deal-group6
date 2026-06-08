package ie.ucd.bdic.group6.core.card;

import ie.ucd.bdic.group6.core.card.deck.Deck;
import ie.ucd.bdic.group6.core.card.deck.DeckDefinition;
import ie.ucd.bdic.group6.core.card.deck.StandardDeckFactory;
import ie.ucd.bdic.group6.core.property.PropertyColor;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

class StandardDeckFactoryTest {

    @Test
    void deckHas106CardsAndExpectedCounts() {
        List<Card> deck = StandardDeckFactory.createUnshuffledDeck();
        assertEquals(DeckDefinition.TOTAL_PLAY_CARDS, deck.size());

        Map<CardType, Long> byType = deck.stream().collect(Collectors.groupingBy(Card::type, Collectors.counting()));
        assertEquals(20L, byType.get(CardType.MONEY));
        assertEquals(28L, byType.get(CardType.PROPERTY));
        assertEquals(11L, byType.get(CardType.PROPERTY_WILD));
        assertEquals(47L, byType.get(CardType.ACTION));

        long passGo = deck.stream()
                .filter(c -> c instanceof ActionCard ac && ac.actionType() == ActionType.PASS_GO)
                .count();
        assertEquals(10L, passGo);

        long wildRent = deck.stream()
                .filter(c -> c instanceof ActionCard ac && ac.actionType() == ActionType.WILD_RENT)
                .count();
        assertEquals(3L, wildRent);

        long moneySum = deck.stream()
                .filter(MoneyCard.class::isInstance)
                .mapToLong(c -> ((MoneyCard) c).valueM())
                .sum();
        assertEquals(57L, moneySum);

        Map<PropertyColor, Long> props = deck.stream()
                .filter(c -> c instanceof PropertyCard)
                .map(c -> ((PropertyCard) c).color())
                .collect(Collectors.groupingBy(c -> c, Collectors.counting()));
        assertEquals(2L, props.get(PropertyColor.BROWN));
        assertEquals(3L, props.get(PropertyColor.LIGHT_BLUE));
        assertEquals(4L, props.get(PropertyColor.RAILROAD));
        assertEquals(2L, props.get(PropertyColor.UTILITY));
    }

    @Test
    void shuffleDeckPreservesSizeAndIds() {
        List<Card> original = StandardDeckFactory.createUnshuffledDeck();
        Deck shuffled = Deck.shuffled(original, new Random(42));
        assertEquals(DeckDefinition.TOTAL_PLAY_CARDS, shuffled.size());
        Map<String, Card> byId = new HashMap<>();
        for (int i = 0; i < DeckDefinition.TOTAL_PLAY_CARDS; i++) {
            Card c = shuffled.draw();
            assertNull(byId.put(c.id(), c));
        }
        assertEquals(DeckDefinition.TOTAL_PLAY_CARDS, byId.size());
        for (Card c : original) {
            assertTrue(byId.containsKey(c.id()), "missing id " + c.id());
        }
    }
}
