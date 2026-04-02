package ie.ucd.bdic.group6.core.card;

import java.util.Map;

public class RuleGuardDecorator extends CardEffectDecorator {
    public RuleGuardDecorator(CardEffect delegate) {
        super(delegate);
    }

    @Override
    public ActionExecutionResult apply(Map<String, Object> payload) {
        if (payload == null || !payload.containsKey("playerId")) {
            return ActionExecutionResult.failure("MISSING_PLAYER_ID");
        }
        return delegate().apply(payload);
    }
}
