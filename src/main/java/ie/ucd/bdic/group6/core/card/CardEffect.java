package ie.ucd.bdic.group6.core.card;

import java.util.Map;

@FunctionalInterface
public interface CardEffect {
    ActionExecutionResult apply(Map<String, Object> payload);
}
