package ie.ucd.bdic.group6.core.rules;

import ie.ucd.bdic.group6.core.property.PropertyBoard;
import ie.ucd.bdic.group6.core.property.PropertyColor;
import ie.ucd.bdic.group6.core.property.PropertySet;

import java.util.List;
import java.util.Objects;

/**
 * Read helpers for rent calculations across a {@link PropertyBoard}. Rent cards charge one selected
 * property color; incomplete rows still count through {@link PropertySet#rentValue()}.
 */
public final class PropertyRentRule {

    public static int maxRentForAnchor(PropertyBoard board, PropertyColor anchor) {
        Objects.requireNonNull(board, "board");
        Objects.requireNonNull(anchor, "anchor");
        return board.rowsView().stream()
                .filter(row -> row.anchorColor() == anchor)
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
}
