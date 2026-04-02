package ie.ucd.bdic.group6.core.card;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class CompositeCardEffect implements CardEffect {
    private final List<CardEffect> children;

    public CompositeCardEffect(List<CardEffect> children) {
        this.children = new ArrayList<>(children == null ? List.of() : children);
    }

    public void add(CardEffect child) {
        if (child != null) {
            children.add(child);
        }
    }

    @Override
    public ActionExecutionResult apply(Map<String, Object> payload) {
        for (CardEffect child : children) {
            ActionExecutionResult result = child.apply(payload);
            if (!result.success()) {
                return result;
            }
        }
        return ActionExecutionResult.success("COMPOSITE_APPLIED:" + children.size());
    }
}

