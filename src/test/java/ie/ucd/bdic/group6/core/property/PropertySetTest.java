package ie.ucd.bdic.group6.core.property;

import ie.ucd.bdic.group6.core.card.PropertyCard;
import ie.ucd.bdic.group6.core.card.PropertyWildCard;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PropertySetTest {

    @Test
    void completeSetAndHouseHotelGating() {
        PropertySet row = new PropertySet(PropertyColor.BROWN);
        PropertyCard p1 = new PropertyCard("t-p1", "B1", PropertyColor.BROWN, 2);
        PropertyCard p2 = new PropertyCard("t-p2", "B2", PropertyColor.BROWN, 2);
        assertTrue(row.tryAdd(p1, Optional.empty()));
        assertFalse(row.canBuildHouseStructure());
        assertTrue(row.tryAdd(p2, Optional.empty()));
        assertTrue(row.isComplete());
        assertTrue(row.canBuildHouseStructure());
        assertTrue(row.tryPlaceHouseStructuralOnly());
        assertTrue(row.canBuildHotelStructure());
        assertTrue(row.tryPlaceHotelStructuralOnly());
        assertFalse(row.canBuildHouseStructure());
    }

    @Test
    void wildMustMatchAnchorAndAllowedColors() {
        PropertySet row = new PropertySet(PropertyColor.PINK);
        PropertyWildCard wild = new PropertyWildCard("t-w1", "P/O", EnumSet.of(PropertyColor.PINK, PropertyColor.ORANGE), 2);
        assertTrue(row.canAdd(wild, Optional.of(PropertyColor.PINK)));
        assertFalse(row.canAdd(wild, Optional.of(PropertyColor.ORANGE)));
        assertTrue(row.tryAdd(wild, Optional.of(PropertyColor.PINK)));
    }
}
