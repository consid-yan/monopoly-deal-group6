package ie.ucd.bdic.group6.core.property;

import ie.ucd.bdic.group6.core.card.Card;
import ie.ucd.bdic.group6.core.card.PropertyCard;
import ie.ucd.bdic.group6.core.card.PropertyWildCard;
import ie.ucd.bdic.group6.core.common.OperationError;
import ie.ucd.bdic.group6.core.common.OperationResult;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * One property row (one pile building toward a single anchor color).
 * Each row has a stable {@link #rowId()} for commands and snapshots (independent of board list index).
 */
public final class PropertySet {

    private final String rowId;
    private final PropertyColor anchorColor;
    private final List<Card> cards = new ArrayList<>();
    private final Map<String, PropertyColor> wildAssignments = new HashMap<>();
    private boolean house;
    private boolean hotel;

    public PropertySet(PropertyColor anchorColor) {
        this(anchorColor, UUID.randomUUID().toString());
    }

    /**
     * Restores or transfers a row while preserving {@code rowId} (snapshots, dealbreaker, etc.).
     */
    public PropertySet(PropertyColor anchorColor, String rowId) {
        this.anchorColor = Objects.requireNonNull(anchorColor, "anchorColor");
        this.rowId = Objects.requireNonNull(rowId, "rowId");
        if (rowId.isBlank()) {
            throw new IllegalArgumentException("rowId must not be blank");
        }
    }

    public String rowId() {
        return rowId;
    }

    public PropertyColor anchorColor() {
        return anchorColor;
    }

    public List<Card> cardsView() {
        return Collections.unmodifiableList(cards);
    }

    public Map<String, PropertyColor> wildAssignmentsView() {
        return Map.copyOf(wildAssignments);
    }

    public boolean contains(Card card) {
        return cards.stream().anyMatch(c -> c.id().equals(card.id()));
    }

    public Optional<PropertyColor> wildAssignmentFor(String cardId) {
        return Optional.ofNullable(wildAssignments.get(cardId));
    }

    public int size() {
        return cards.size();
    }

    public int capacity() {
        return PropertyColor.fullSetSize(anchorColor);
    }

    public boolean isComplete() {
        return size() == capacity();
    }

    public boolean hasHouse() {
        return house;
    }

    public boolean hasHotel() {
        return hotel;
    }

    public boolean canAdd(Card card) {
        return canAdd(card, null);
    }

    public boolean canAdd(Card card, PropertyColor wildChoice) {
        Objects.requireNonNull(card, "card");
        if (size() >= capacity()) {
            return false;
        }
        if (card instanceof PropertyCard pc) {
            return wildChoice == null && pc.color() == anchorColor;
        }
        if (card instanceof PropertyWildCard wc) {
            return wildChoice == anchorColor && wc.allows(wildChoice);
        }
        return false;
    }

    public boolean tryAdd(Card card) {
        return tryAdd(card, null);
    }

    public boolean tryAdd(Card card, PropertyColor wildChoice) {
        if (!canAdd(card, wildChoice)) {
            return false;
        }
        cards.add(card);
        if (card instanceof PropertyWildCard wc) {
            wildAssignments.put(wc.id(), wildChoice);
        }
        return true;
    }

    public boolean remove(Card card) {
        boolean removed = cards.removeIf(c -> c.id().equals(card.id()));
        if (removed) {
            wildAssignments.remove(card.id());
            if (!isComplete()) {
                house = false;
                hotel = false;
            }
        }
        return removed;
    }

    /**
     * Structural rules only (complete set, not railroad/utility, no existing improvements).
     * Gameplay must spend a House action card via {@code Player.trySpendHouseCardOnRow}.
     */
    public boolean canBuildHouseStructure() {
        return validateHouseStructure().isSuccess();
    }

    /**
     * Structural rules: complete, not rail/util, has house, no hotel.
     */
    public boolean canBuildHotelStructure() {
        return validateHotelStructure().isSuccess();
    }

    public OperationResult<Void> validateHouseStructure() {
        return validateImprovementStructure(false);
    }

    public OperationResult<Void> validateHotelStructure() {
        return validateImprovementStructure(true);
    }

    private OperationResult<Void> validateImprovementStructure(boolean forHotel) {
        if (!isComplete()) {
            return OperationResult.fail(OperationError.NOT_COMPLETE_SET, "row is not a complete property set");
        }
        if (PropertyColor.isRailOrUtility(anchorColor)) {
            return OperationResult.fail(
                    OperationError.RAIL_OR_UTILITY_CANNOT_BUILD,
                    "railroad and utility sets cannot receive " + (forHotel ? "hotels" : "houses"));
        }
        if (forHotel && !house) {
            return OperationResult.fail(OperationError.HOUSE_REQUIRED_BEFORE_HOTEL, "a house is required before a hotel");
        }
        if (!forHotel && house) {
            return OperationResult.fail(OperationError.HOUSE_ALREADY_BUILT, "row already has a house");
        }
        if (hotel) {
            return OperationResult.fail(OperationError.HOTEL_ALREADY_BUILT, "row already has a hotel");
        }
        return OperationResult.ok();
    }

    /**
     * Places a house token on this row after the House action card has been spent by the engine/player layer.
     * Does not validate the House card itself.
     */
    public OperationResult<Void> tryPlaceHouseStructuralOnly() {
        OperationResult<Void> validation = validateHouseStructure();
        if (validation.isSuccess()) {
            house = true;
        }
        return validation;
    }

    /**
     * Places a hotel token after the Hotel action card has been spent and a house already exists.
     */
    public OperationResult<Void> tryPlaceHotelStructuralOnly() {
        OperationResult<Void> validation = validateHotelStructure();
        if (validation.isSuccess()) {
            hotel = true;
        }
        return validation;
    }

    /**
     * Snapshot restore helper: reapplies house/hotel flags after cards are restored.
     * Clearing improvements ({@code houseFlag == false && hotelFlag == false}) is allowed on incomplete sets.
     */
    public void applyImprovementFlagsForSnapshot(boolean houseFlag, boolean hotelFlag) {
        if (!houseFlag && !hotelFlag) {
            this.house = false;
            this.hotel = false;
            return;
        }
        if (!isComplete()) {
            throw new IllegalStateException("cannot apply improvements on incomplete set");
        }
        if (PropertyColor.isRailOrUtility(anchorColor)) {
            this.house = false;
            this.hotel = false;
            return;
        }
        this.house = houseFlag;
        this.hotel = hotelFlag && this.house;
    }

    /**
     * [Deep Dive: Rent Calculation — How is the rent amount determined?]
     * Two-tier calculation:
     *   1. Incomplete set → RentTable.baseRent(color, cardCount)
     *      e.g., 2 red cards = $3M, 1 red card = $1M
     *   2. Complete set → RentTable.completeSetRent(color, hasHouse, hasHotel)
     *      e.g., full red set = $4M, +House = $5M, +Hotel = $7M (values from official rules)
     * The PropertyRentRule.maxRentForAnchor() picks the best (highest) row for a given color
     * when a player owns multiple rows of the same color.
     */
    public int rentValue() {
        if (!isComplete()) {
            return RentTable.baseRent(anchorColor, size());
        }
        return RentTable.completeSetRent(anchorColor, house, hotel);
    }
}
