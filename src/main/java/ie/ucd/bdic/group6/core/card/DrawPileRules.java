package ie.ucd.bdic.group6.core.card;

import ie.ucd.bdic.group6.core.common.OperationError;
import ie.ucd.bdic.group6.core.common.OperationResult;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Random;

/**
 * Session-level draw/discard rules. The engine owns the discard pile list; this class defines how to refill an empty
 * {@link Deck} from that list.
 */
public final class DrawPileRules {

    private DrawPileRules() {
    }

    /**
     * When the draw pile is empty, moves all cards from {@code discardPile} into {@code drawPile} after shuffling.
     * Clears {@code discardPile} on success.
     *
     * @return number of cards moved into the draw pile
     */
    public static OperationResult<Integer> reshuffleDiscardIntoDrawPile(
            Deck drawPile, List<Card> discardPile, Random random) {
        Objects.requireNonNull(drawPile, "drawPile");
        Objects.requireNonNull(discardPile, "discardPile");
        Objects.requireNonNull(random, "random");
        if (!drawPile.isEmpty()) {
            return OperationResult.fail(OperationError.DRAW_PILE_NOT_EMPTY, "draw pile is not empty");
        }
        if (discardPile.isEmpty()) {
            return OperationResult.fail(OperationError.EMPTY_DISCARD_PILE, "discard pile is empty");
        }
        List<Card> taken = new ArrayList<>(discardPile);
        discardPile.clear();
        drawPile.refillFromShuffled(taken, random);
        return OperationResult.ok(taken.size());
    }
}
