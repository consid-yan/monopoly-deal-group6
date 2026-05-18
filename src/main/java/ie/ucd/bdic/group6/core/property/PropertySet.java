package ie.ucd.bdic.group6.core.property;

import ie.ucd.bdic.group6.core.card.Card;
import ie.ucd.bdic.group6.core.card.PropertyCard;
import ie.ucd.bdic.group6.core.card.PropertyWildCard;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * One property row (one pile building toward a single anchor color).
 */
public final class PropertySet {

    private final PropertyColor anchorColor;
    private final List<Card> cards = new ArrayList<>();
    private final Map<String, PropertyColor> wildAssignments = new HashMap<>();
    private boolean house;
    private boolean hotel;

    public PropertySet(PropertyColor anchorColor) {
        this.anchorColor = Objects.requireNonNull(anchorColor, "anchorColor");
    }

    public PropertyColor anchorColor() {
        return anchorColor;
    }

    public List<Card> cardsView() {
        return Collections.unmodifiableList(cards);
    }

    public Map<String, PropertyColor> wildAssignmentsView() {
        return Collections.unmodifiableMap(new HashMap<>(wildAssignments));
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

    /**
     * @param wildChoice required when {@code card} is a {@link PropertyWildCard}; must equal {@link #anchorColor()}
     *                   and be allowed by the wild card.
     */
    public boolean canAdd(Card card, Optional<PropertyColor> wildChoice) {
        Objects.requireNonNull(card, "card");
        if (size() >= capacity()) {
            return false;
        }
        if (card instanceof PropertyCard pc) {
            return wildChoice.isEmpty() && pc.color() == anchorColor;
        }
        if (card instanceof PropertyWildCard wc) {
            if (wildChoice.isEmpty()) {
                return false;
            }
            PropertyColor choice = wildChoice.get();
            return wc.allows(choice) && choice == anchorColor;
        }
        return false;
    }

    public boolean tryAdd(Card card, Optional<PropertyColor> wildChoice) {
        if (!canAdd(card, wildChoice)) {
            return false;
        }
        if (card instanceof PropertyCard pc) {
            cards.add(pc);
            return true;
        }
        if (card instanceof PropertyWildCard wc) {
            PropertyColor choice = wildChoice.orElseThrow();
            cards.add(wc);
            wildAssignments.put(wc.id(), choice);
            return true;
        }
        return false;
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
        return isComplete()
                && !PropertyColor.isRailOrUtility(anchorColor)
                && !house
                && !hotel;
    }

    /**
     * Structural rules: complete, not rail/util, has house, no hotel.
     */
    public boolean canBuildHotelStructure() {
        return isComplete()
                && !PropertyColor.isRailOrUtility(anchorColor)
                && house
                && !hotel;
    }

    /**
     * Places a house token on this row after the House action card has been spent by the engine/player layer.
     * Does not validate the House card itself.
     */
    public boolean tryPlaceHouseStructuralOnly() {
        if (!canBuildHouseStructure()) {
            return false;
        }
        house = true;
        return true;
    }

    /**
     * Places a hotel token after the Hotel action card has been spent and a house already exists.
     */
    public boolean tryPlaceHotelStructuralOnly() {
        if (!canBuildHotelStructure()) {
            return false;
        }
        hotel = true;
        return true;
    }

    /**
     * Snapshot restore helper: reapplies house/hotel flags after cards are restored.
     */
    public void applyImprovementFlagsForSnapshot(boolean houseFlag, boolean hotelFlag) {
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
     * Chargeable rent for this row using {@link RentTable}. Incomplete sets use {@link RentTable#baseRent};
     * complete sets include house/hotel tiers when built.
     */
    public int rentValue() {
        if (!isComplete()) {
            return RentTable.baseRent(anchorColor, size());
        }
        return RentTable.completeSetRent(anchorColor, house, hotel);
    }

    public void resetImprovementsIfIncomplete() {
        if (!isComplete()) {
            house = false;
            hotel = false;
        }
    }
}
