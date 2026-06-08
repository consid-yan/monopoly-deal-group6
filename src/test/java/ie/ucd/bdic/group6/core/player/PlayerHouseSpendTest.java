package ie.ucd.bdic.group6.core.player;

import ie.ucd.bdic.group6.core.card.ActionCard;
import ie.ucd.bdic.group6.core.card.ActionType;
import ie.ucd.bdic.group6.core.card.PropertyCard;
import ie.ucd.bdic.group6.core.common.OperationError;
import ie.ucd.bdic.group6.core.common.OperationResult;
import ie.ucd.bdic.group6.core.property.PropertyColor;
import ie.ucd.bdic.group6.core.property.PropertySet;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlayerHouseSpendTest {

    @Test
    void spendingHouseCardFromHandPlacesHouse() {
        Player p = new Player("p1", "A");
        ActionCard house = new ActionCard("ac-house", "House", ActionType.HOUSE, 3, null);
        p.hand().add(house);
        PropertyCard b1 = new PropertyCard("b1", "B1", PropertyColor.BROWN, 2);
        PropertyCard b2 = new PropertyCard("b2", "B2", PropertyColor.BROWN, 2);
        PropertySet row = new PropertySet(PropertyColor.BROWN);
        assertTrue(row.tryAdd(b1));
        assertTrue(row.tryAdd(b2));
        p.propertyBoard().addRow(row);

        assertTrue(p.trySpendHouseCardOnRow(house, row).isSuccess());
        assertFalse(p.hand().contains(house));
        assertTrue(row.hasHouse());
    }

    @Test
    void failedImprovementSpendPreservesSpecificReasonAndReturnsCardToHand() {
        Player p = new Player("p1", "A");
        ActionCard hotel = new ActionCard("ac-hotel", "Hotel", ActionType.HOTEL, 4, null);
        p.hand().add(hotel);
        PropertyCard b1 = new PropertyCard("b1", "B1", PropertyColor.BROWN, 2);
        PropertyCard b2 = new PropertyCard("b2", "B2", PropertyColor.BROWN, 2);
        PropertySet row = new PropertySet(PropertyColor.BROWN);
        assertTrue(row.tryAdd(b1));
        assertTrue(row.tryAdd(b2));
        p.propertyBoard().addRow(row);

        OperationResult<ActionCard> result = p.trySpendHotelCardOnRow(hotel, row);

        assertFalse(result.isSuccess());
        assertEquals(OperationError.HOUSE_REQUIRED_BEFORE_HOTEL, result.errorCode().orElseThrow());
        assertTrue(p.hand().contains(hotel));
        assertFalse(row.hasHotel());
    }
}
