package ie.ucd.bdic.group6.core.property;

import java.util.List;

/**
 * Read helpers for rent calculations across a {@link PropertyBoard}.
 */
public final class PropertyRentSupport {

    private PropertyRentSupport() {
    }

    public static List<PropertySet> rowsWithAnchor(PropertyBoard board, PropertyColor anchor) {
        return board.rowsView().stream().filter(r -> r.anchorColor() == anchor).toList();
    }

    /**
     * Maximum rent among complete rows for a given anchor color (useful when multiple incomplete rows exist).
     */
    public static int maxCompleteRowRentForAnchor(PropertyBoard board, PropertyColor anchor) {
        return board.rowsView().stream()
                .filter(r -> r.anchorColor() == anchor && r.isComplete())
                .mapToInt(PropertySet::rentValue)
                .max()
                .orElse(0);
    }

    /**
     * Sum of {@link PropertySet#rentValue()} across all complete rows (not the same as charging rent for one color).
     */
    public static int sumCompleteRowRents(PropertyBoard board) {
        return board.rowsView().stream()
                .filter(PropertySet::isComplete)
                .mapToInt(PropertySet::rentValue)
                .sum();
    }
}
