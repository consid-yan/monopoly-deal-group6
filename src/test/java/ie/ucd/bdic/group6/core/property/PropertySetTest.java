package ie.ucd.bdic.group6.core.property;

import ie.ucd.bdic.group6.core.card.PropertyCard;
import ie.ucd.bdic.group6.core.card.PropertyWildCard;
import ie.ucd.bdic.group6.core.common.OperationError;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PropertySetTest {

    @Test
    void rowIdIsStableWhenProvided() {
        PropertySet row = new PropertySet(PropertyColor.BROWN, "fixed-row-id");
        assertEquals("fixed-row-id", row.rowId());
    }

    @Test
    void completeSetAndHouseHotelGating() {
        PropertySet row = new PropertySet(PropertyColor.BROWN);
        PropertyCard p1 = new PropertyCard("t-p1", "B1", PropertyColor.BROWN, 2);
        PropertyCard p2 = new PropertyCard("t-p2", "B2", PropertyColor.BROWN, 2);
        assertTrue(row.tryAdd(p1));
        assertFalse(row.canBuildHouseStructure());
        assertTrue(row.tryAdd(p2));
        assertTrue(row.isComplete());
        assertTrue(row.canBuildHouseStructure());
        assertTrue(row.tryPlaceHouseStructuralOnly().isSuccess());
        assertTrue(row.canBuildHotelStructure());
        assertTrue(row.tryPlaceHotelStructuralOnly().isSuccess());
        assertFalse(row.canBuildHouseStructure());
    }

    @Test
    void structuralImprovementFailuresReturnSpecificReasons() {
        PropertySet incomplete = new PropertySet(PropertyColor.BROWN);
        assertEquals(OperationError.NOT_COMPLETE_SET,
                incomplete.tryPlaceHouseStructuralOnly().errorCode().orElseThrow());

        PropertySet railroad = new PropertySet(PropertyColor.RAILROAD);
        assertTrue(railroad.tryAdd(new PropertyCard("rail-1", "Rail 1", PropertyColor.RAILROAD, 4)));
        assertTrue(railroad.tryAdd(new PropertyCard("rail-2", "Rail 2", PropertyColor.RAILROAD, 4)));
        assertTrue(railroad.tryAdd(new PropertyCard("rail-3", "Rail 3", PropertyColor.RAILROAD, 4)));
        assertTrue(railroad.tryAdd(new PropertyCard("rail-4", "Rail 4", PropertyColor.RAILROAD, 4)));
        assertEquals(OperationError.RAIL_OR_UTILITY_CANNOT_BUILD,
                railroad.tryPlaceHouseStructuralOnly().errorCode().orElseThrow());

        PropertySet brown = new PropertySet(PropertyColor.BROWN);
        assertTrue(brown.tryAdd(new PropertyCard("brown-1", "Brown 1", PropertyColor.BROWN, 2)));
        assertTrue(brown.tryAdd(new PropertyCard("brown-2", "Brown 2", PropertyColor.BROWN, 2)));
        assertEquals(OperationError.HOUSE_REQUIRED_BEFORE_HOTEL,
                brown.tryPlaceHotelStructuralOnly().errorCode().orElseThrow());
        assertTrue(brown.tryPlaceHouseStructuralOnly().isSuccess());
        assertEquals(OperationError.HOUSE_ALREADY_BUILT,
                brown.tryPlaceHouseStructuralOnly().errorCode().orElseThrow());
        assertTrue(brown.tryPlaceHotelStructuralOnly().isSuccess());
        assertEquals(OperationError.HOTEL_ALREADY_BUILT,
                brown.tryPlaceHotelStructuralOnly().errorCode().orElseThrow());
    }

    @Test
    void wildMustMatchAnchorAndAllowedColors() {
        PropertySet row = new PropertySet(PropertyColor.PINK);
        PropertyWildCard wild = new PropertyWildCard("t-w1", "P/O", EnumSet.of(PropertyColor.PINK, PropertyColor.ORANGE), 2);
        assertTrue(row.canAdd(wild, PropertyColor.PINK));
        assertFalse(row.canAdd(wild, PropertyColor.ORANGE));
        assertTrue(row.tryAdd(wild, PropertyColor.PINK));
    }
}
