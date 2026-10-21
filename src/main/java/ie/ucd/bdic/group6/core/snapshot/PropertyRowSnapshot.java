package ie.ucd.bdic.group6.core.snapshot;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

public record PropertyRowSnapshot(
        String rowId,
        String anchorColor,
        boolean complete,
        int rentValue,
        boolean hasHouse,
        boolean hasHotel,
        List<CardSnapshot> cards
) implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    public PropertyRowSnapshot {
        rowId = text(rowId);
        anchorColor = text(anchorColor);
        cards = cards == null ? List.of() : List.copyOf(cards);
    }

    private static String text(String value) {
        return value == null ? "" : value;
    }
}
