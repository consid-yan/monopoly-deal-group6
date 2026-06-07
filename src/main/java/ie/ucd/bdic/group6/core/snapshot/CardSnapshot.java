package ie.ucd.bdic.group6.core.snapshot;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

public record CardSnapshot(
        String id,
        String type,
        String displayName,
        int bankValueM,
        int valueM,
        String propertyColor,
        int requiredToComplete,
        List<String> allowedColors,
        String actionType,
        List<String> rentColorOptions
) implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    public CardSnapshot {
        id = text(id);
        type = text(type);
        displayName = text(displayName);
        propertyColor = text(propertyColor);
        allowedColors = copy(allowedColors);
        actionType = text(actionType);
        rentColorOptions = copy(rentColorOptions);
    }

    private static List<String> copy(List<String> values) {
        return values == null ? List.of() : List.copyOf(values);
    }

    private static String text(String value) {
        return value == null ? "" : value;
    }
}
