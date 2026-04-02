package ie.ucd.bdic.group6.core.card;

import java.util.Map;

public abstract class CardEffectDecorator implements CardEffect {
    private final CardEffect delegate;

    protected CardEffectDecorator(CardEffect delegate) {
        this.delegate = delegate;
    }

    protected CardEffect delegate() {
        return delegate;
    }

    @Override
    public ActionExecutionResult apply(Map<String, Object> payload) {
        return delegate.apply(payload);
    }
}

