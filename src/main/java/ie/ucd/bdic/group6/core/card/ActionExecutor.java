package ie.ucd.bdic.group6.core.card;

import java.util.List;
import java.util.Map;

public class ActionExecutor {
    public ActionExecutionResult executePlayCard(Map<String, Object> payload) {
        Map<String, Object> safePayload = payload == null ? Map.of() : payload;

        CardEffect composite = new CompositeCardEffect(List.of(new LeafCardEffect("PLAY_CARD")));
        CardEffect guarded = new RuleGuardDecorator(composite);
        CardEffect audited = new AuditTagDecorator(guarded);

        return audited.apply(safePayload);
    }
}
