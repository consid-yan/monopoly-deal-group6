package ie.ucd.bdic.group6.core.property;

import java.util.List;
import java.util.Objects;

/**
 * Read helpers for rent calculations across a {@link PropertyBoard}. Rent cards charge one selected
 * property color; incomplete rows still count through {@link PropertySet#rentValue()}.
 */
public final class PropertyRentSupport {

    private PropertyRentSupport() {
    }

    public static int maxRentForAnchor(PropertyBoard board, PropertyColor anchor) {
        return rowsWithAnchor(board, anchor).stream()
                .mapToInt(PropertySet::rentValue)
                .max()
                .orElse(0);
    }

    public static List<PropertyColor> rentableAnchors(PropertyBoard board) {
        Objects.requireNonNull(board, "board");
        return board.rowsView().stream()
                .filter(row -> row.rentValue() > 0)
                .map(PropertySet::anchorColor)
                .distinct()
                .toList();
    }

    private static List<PropertySet> rowsWithAnchor(PropertyBoard board, PropertyColor anchor) {
        Objects.requireNonNull(board, "board");
        Objects.requireNonNull(anchor, "anchor");
        return board.rowsView().stream()
                .filter(row -> row.anchorColor() == anchor)
                .filter(row -> row.rentValue() > 0)
                .toList();
    }
}
