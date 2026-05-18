package ie.ucd.bdic.group6.core.player;

import ie.ucd.bdic.group6.core.card.MoneyCard;
import ie.ucd.bdic.group6.core.card.PropertyCard;
import ie.ucd.bdic.group6.core.property.PropertyColor;
import ie.ucd.bdic.group6.core.property.PropertySet;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlayerSnapshotTest {

    @Test
    void snapshotRoundTripPreservesZones() {
        Player p = new Player("p1", "A");
        MoneyCard m = new MoneyCard("m-1", "1M", 1);
        PropertyCard prop = new PropertyCard("pr-1", "Brown 1", PropertyColor.BROWN, 2);
        p.hand().add(m);
        p.bank().forceAddForTestsOrMigration(new MoneyCard("m-2", "2M", 2));
        var row = new PropertySet(PropertyColor.BROWN);
        assertTrue(row.tryAdd(prop, Optional.empty()));
        p.propertyBoard().addRow(row);

        PlayerSnapshot snap = p.snapshot();
        Player p2 = new Player("p1", "B");
        p2.restore(snap);

        assertEquals(1, p2.hand().size());
        assertEquals(1, p2.bank().size());
        assertEquals(1, p2.propertyBoard().rowsView().size());
        assertEquals(1, p2.propertyBoard().rowsView().getFirst().size());
    }
}
