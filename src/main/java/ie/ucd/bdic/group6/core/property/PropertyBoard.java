package ie.ucd.bdic.group6.core.property;

import ie.ucd.bdic.group6.core.card.Card;
import ie.ucd.bdic.group6.core.common.OperationError;
import ie.ucd.bdic.group6.core.common.OperationResult;
import ie.ucd.bdic.group6.core.rules.PropertyPaymentRule;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * All property rows in front of one player.
 */
public final class PropertyBoard {

    private final List<PropertySet> rows = new ArrayList<>();

    public List<PropertySet> rowsView() {
        return Collections.unmodifiableList(rows);
    }

    public boolean ownsRow(PropertySet row) {
        return rows.contains(row);
    }

    public Optional<PropertySet> findRowById(String rowId) {
        if (rowId == null || rowId.isBlank()) {
            return Optional.empty();
        }
        return rows.stream().filter(r -> r.rowId().equals(rowId)).findFirst();
    }

    public PropertySet startNewRow(PropertyColor anchorColor) {
        for (PropertySet existing : rows) {
            if (existing.anchorColor() == anchorColor && !existing.isComplete()) {
                return existing;
            }
        }
        PropertySet row = new PropertySet(anchorColor);
        rows.add(row);
        return row;
    }

    public void addRow(PropertySet row) {
        rows.add(Objects.requireNonNull(row, "row"));
    }

    /**
     * Adds {@code card} to an existing row owned by this board.
     */
    public OperationResult<Void> tryAddCardToRow(PropertySet row, Card card) {
        return tryAddCardToRow(row, card, null);
    }

    public OperationResult<Void> tryAddCardToRow(PropertySet row, Card card, PropertyColor wildChoice) {
        Objects.requireNonNull(row, "row");
        Objects.requireNonNull(card, "card");
        if (!ownsRow(row)) {
            return OperationResult.fail(OperationError.ROW_NOT_OWNED_BY_PLAYER, "row not on this board");
        }
        if (!row.tryAdd(card, wildChoice)) {
            if (row.size() >= row.capacity()) {
                return OperationResult.fail(OperationError.ROW_FULL, "row is full");
            }
            return OperationResult.fail(
                    OperationError.PROPERTY_TYPE_MISMATCH, "card cannot join this row with given wild choice");
        }
        return OperationResult.ok();
    }

    public Optional<PropertySet> findRowContaining(Card card) {
        Objects.requireNonNull(card, "card");
        for (PropertySet row : rows) {
            if (row.contains(card)) {
                return Optional.of(row);
            }
        }
        return Optional.empty();
    }

    public boolean removeRow(PropertySet row) {
        return rows.remove(row);
    }

    public boolean canPayWithPropertyCard(Card card) {
        return findRowContaining(card).isPresent() && PropertyPaymentRule.canUseAsPropertyPayment(card);
    }

    /**
     * Sly Deal and similar effects cannot remove a card from a complete set.
     */
    public boolean canRemoveCardForSteal(Card card) {
        return findRowContaining(card).map(row -> !row.isComplete()).orElse(false);
    }

    public void compactEmptyRows() {
        rows.removeIf(r -> r.size() == 0);
    }

    public void clearAllRows() {
        rows.clear();
    }

    public Set<PropertyColor> distinctCompletedAnchorColors() {
        EnumSet<PropertyColor> colors = EnumSet.noneOf(PropertyColor.class);
        for (PropertySet row : rows) {
            if (row.isComplete()) {
                colors.add(row.anchorColor());
            }
        }
        return colors;
    }

    public int completeDistinctColorCount() {
        return distinctCompletedAnchorColors().size();
    }
}
