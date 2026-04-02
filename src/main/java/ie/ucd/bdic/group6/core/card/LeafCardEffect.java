package ie.ucd.bdic.group6.core.card;

import java.util.Map;

public class LeafCardEffect implements CardEffect {
    private final String effectName;

    public LeafCardEffect(String effectName) {
        this.effectName = effectName;
    }

    @Override
    public ActionExecutionResult apply(Map<String, Object> payload) {
        return ActionExecutionResult.success("LEAF_APPLIED:" + effectName);
    }
}

