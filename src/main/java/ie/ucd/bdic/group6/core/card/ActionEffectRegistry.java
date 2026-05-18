package ie.ucd.bdic.group6.core.card;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Engine-owned registry mapping {@link ActionType} to {@link ActionEffect} implementations.
 */
public final class ActionEffectRegistry {

    private final Map<ActionType, ActionEffect> effects = new ConcurrentHashMap<>();

    public void register(ActionType type, ActionEffect effect) {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(effect, "effect");
        effects.put(type, effect);
    }

    public Optional<ActionEffect> effectFor(ActionType type) {
        return Optional.ofNullable(effects.get(type));
    }
}
