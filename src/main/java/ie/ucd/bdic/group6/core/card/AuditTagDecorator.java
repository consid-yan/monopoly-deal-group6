package ie.ucd.bdic.group6.core.card;

import java.util.Map;

public class AuditTagDecorator extends CardEffectDecorator {
    public AuditTagDecorator(CardEffect delegate) {
        super(delegate);
    }

    @Override
    public ActionExecutionResult apply(Map<String, Object> payload) {
        ActionExecutionResult result = delegate().apply(payload);
        String prefix = result.success() ? "AUDIT_OK:" : "AUDIT_FAIL:";
        if (result.success()) {
            return ActionExecutionResult.success(prefix + result.message());
        }
        return ActionExecutionResult.failure(prefix + result.message());
    }
}

