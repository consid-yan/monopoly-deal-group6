package ie.ucd.bdic.group6.core.player;

import ie.ucd.bdic.group6.core.card.ActionCard;
import ie.ucd.bdic.group6.core.card.ActionType;
import ie.ucd.bdic.group6.core.card.PropertyCard;
import ie.ucd.bdic.group6.core.property.PropertyColor;
import ie.ucd.bdic.group6.core.property.PropertySet;
import org.junit.jupiter.api.Test;

import java.util.Optional;

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
        assertTrue(row.tryAdd(b1, Optional.empty()));
        assertTrue(row.tryAdd(b2, Optional.empty()));
        p.propertyBoard().addRow(row);

        assertTrue(p.trySpendHouseCardOnRow(house, row).isSuccess());
        assertFalse(p.hand().contains(house));
        assertTrue(row.hasHouse());
    }
}
