package ie.ucd.bdic.group6.core.property;

import ie.ucd.bdic.group6.core.card.MoneyCard;
import ie.ucd.bdic.group6.core.card.PropertyCard;
import ie.ucd.bdic.group6.core.rules.PropertyPaymentRule;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PropertyPaymentRuleTest {

    @Test
    void paymentValueUsesCardFaceValueNotRowRent() {
        PropertySet row = new PropertySet(PropertyColor.LIGHT_BLUE);
        PropertyCard c1 = new PropertyCard("lb1", "LB1", PropertyColor.LIGHT_BLUE, 3);
        PropertyCard c2 = new PropertyCard("lb2", "LB2", PropertyColor.LIGHT_BLUE, 3);
        assertTrue(row.tryAdd(c1));
        assertTrue(row.tryAdd(c2));
        assertEquals(2, row.rentValue());
        assertEquals(1, PropertyPaymentRule.paymentValueM(row, c1).value().orElseThrow());
    }

    @Test
    void propertyFaceValuesUseOfficialColorMapping() {
        Map<PropertyColor, Integer> expectedValues = Map.of(
                PropertyColor.BROWN, 1,
                PropertyColor.LIGHT_BLUE, 1,
                PropertyColor.PINK, 2,
                PropertyColor.ORANGE, 2,
                PropertyColor.RAILROAD, 2,
                PropertyColor.UTILITY, 2,
                PropertyColor.RED, 3,
                PropertyColor.YELLOW, 3,
                PropertyColor.GREEN, 4,
                PropertyColor.DARK_BLUE, 4
        );

        for (Map.Entry<PropertyColor, Integer> entry : expectedValues.entrySet()) {
            PropertyColor color = entry.getKey();
            PropertySet row = new PropertySet(color);
            PropertyCard card = new PropertyCard(color.name(), color.name(), color, PropertyColor.fullSetSize(color));
            assertTrue(row.tryAdd(card));
            assertEquals(entry.getValue(), PropertyPaymentRule.paymentValueM(row, card).value().orElseThrow());
        }
    }

    @Test
    void moneyCardCannotPayAsProperty() {
        PropertySet row = new PropertySet(PropertyColor.BROWN);
        MoneyCard money = new MoneyCard("m1", "1M", 1);
        assertFalse(PropertyPaymentRule.canUseAsPropertyPayment(money));
        assertFalse(PropertyPaymentRule.paymentValueM(row, money).isSuccess());
    }
}
