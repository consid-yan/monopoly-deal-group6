package ie.ucd.bdic.group6.core.property;

import ie.ucd.bdic.group6.core.card.Card;
import ie.ucd.bdic.group6.core.common.OperationError;
import ie.ucd.bdic.group6.core.common.OperationResult;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * All property rows in front of one player.
 */
public final class PropertyBoard {

    private final List<PropertySet> rows = new ArrayList<>();
    private final List<Runnable> mutateListeners = new CopyOnWriteArrayList<>();

    public void addMutationListener(Runnable listener) {
        if (listener != null) {
            mutateListeners.add(listener);
        }
    }

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
        fireChanged();
        return row;
    }

    public void addRow(PropertySet row) {
        rows.add(Objects.requireNonNull(row, "row"));
        fireChanged();
    }

    /**
     * Adds {@code card} to an existing row owned by this board.
     */
    public OperationResult<Void> tryAddCardToRow(PropertySet row, Card card, Optional<PropertyColor> wildChoice) {
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
        fireChanged();
        return OperationResult.ok();
    }

    public Optional<PropertySet> findRowContaining(Card card) {
        String id = card.id();
        for (PropertySet row : rows) {
            if (row.cardsView().stream().anyMatch(c -> c.id().equals(id))) {
                return Optional.of(row);
            }
        }
        return Optional.empty();
    }

    /**
     * Removes a property/wild card from whichever row contains it.
     */
    public boolean removeCardFromRows(Card card) {
        for (PropertySet row : rows) {
            if (row.remove(card)) {
                fireChanged();
                return true;
            }
        }
        return false;
    }

    public boolean removeRow(PropertySet row) {
        if (rows.remove(row)) {
            fireChanged();
            return true;
        }
        return false;
    }

    public boolean canPayWithPropertyCard(Card card) {
        return findRowContaining(card)
                .filter(row -> PropertyPaymentSupport.canUseAsPropertyPayment(card, row))
                .isPresent();
    }

    /**
     * Sly Deal and similar effects cannot remove a card from a complete set.
     */
    public boolean canRemoveCardForSteal(Card card) {
        return findRowContaining(card).map(row -> !row.isComplete()).orElse(false);
    }

    public void compactEmptyRows() {
        rows.removeIf(r -> r.size() == 0);
        fireChanged();
    }

    public void clearAllRows() {
        rows.clear();
        fireChanged();
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

    private void fireChanged() {
        for (Runnable r : mutateListeners) {
            r.run();
        }
    }
}
