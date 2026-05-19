package ie.ucd.bdic.group6.core.player;

import ie.ucd.bdic.group6.core.card.Card;
import ie.ucd.bdic.group6.core.card.MoneyCard;
import ie.ucd.bdic.group6.core.common.OperationResult;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BankPaymentSolverTest {

    @Test
    void minimalOverpayFindsExactSubsetWhenPossible() {
        List<Card> bank = List.of(
                new MoneyCard("m1", "1", 1),
                new MoneyCard("m2", "2", 2),
                new MoneyCard("m5", "5", 5));
        OperationResult<List<Card>> r = BankPaymentSolver.suggestMinimalOverpaySubset(bank, 3);
        assertTrue(r.isSuccess());
        int sum = r.value().orElseThrow().stream().mapToInt(c -> c.bankValueM().orElseThrow()).sum();
        assertEquals(3, sum);
    }

    @Test
    void minimalOverpayWhenExactSubsetImpossible() {
        List<Card> bank = List.of(
                new MoneyCard("m1", "1", 1),
                new MoneyCard("m2", "2", 2),
                new MoneyCard("m5", "5", 5));
        OperationResult<List<Card>> r = BankPaymentSolver.suggestMinimalOverpaySubset(bank, 4);
        assertTrue(r.isSuccess());
        int sum = r.value().orElseThrow().stream().mapToInt(c -> c.bankValueM().orElseThrow()).sum();
        assertEquals(5, sum);
    }
}
