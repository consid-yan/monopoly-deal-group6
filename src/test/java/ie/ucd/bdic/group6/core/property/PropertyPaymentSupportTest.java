package ie.ucd.bdic.group6.core.property;

import ie.ucd.bdic.group6.core.card.MoneyCard;
import ie.ucd.bdic.group6.core.card.PropertyCard;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PropertyPaymentSupportTest {

    @Test
    void paymentValueUsesRowRentBeforeCardLeaves() {
        PropertySet row = new PropertySet(PropertyColor.LIGHT_BLUE);
        PropertyCard c1 = new PropertyCard("lb1", "LB1", PropertyColor.LIGHT_BLUE, 1);
        PropertyCard c2 = new PropertyCard("lb2", "LB2", PropertyColor.LIGHT_BLUE, 1);
        assertTrue(row.tryAdd(c1, Optional.empty()));
        assertTrue(row.tryAdd(c2, Optional.empty()));
        assertEquals(3, PropertyPaymentSupport.paymentValueM(row, c1).value().orElseThrow());
    }

    @Test
    void moneyCardCannotPayAsProperty() {
        PropertySet row = new PropertySet(PropertyColor.BROWN);
        MoneyCard money = new MoneyCard("m1", "1M", 1);
        assertFalse(PropertyPaymentSupport.canUseAsPropertyPayment(money));
        assertFalse(PropertyPaymentSupport.paymentValueM(row, money).isSuccess());
    }
}
