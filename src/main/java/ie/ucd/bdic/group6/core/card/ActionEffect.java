package ie.ucd.bdic.group6.core.card;

import ie.ucd.bdic.group6.core.common.OperationResult;

import java.util.List;
import java.util.Objects;

/**
 * Minimal extension point for action execution: the engine registers implementations per {@link ActionType}.
 */
@FunctionalInterface
public interface ActionEffect {

    OperationResult<Void> execute(ActionContext context);

    /**
     * Game-facing context passed to effects. Keep fields minimal; engine can wrap richer session state.
     */
    record ActionContext(
            String sessionId,
            String actingPlayerId,
            ActionCard playedCard,
            List<PlayerRef> players,
            Deck drawPile) {

        public ActionContext {
            Objects.requireNonNull(playedCard, "playedCard");
            players = players == null ? List.of() : List.copyOf(players);
        }
    }

    /**
     * Lightweight player reference so {@link ActionContext} does not depend on {@code core.player}.
     */
    record PlayerRef(String id, String name) {
        public PlayerRef {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(name, "name");
        }
    }
}
