package ie.ucd.bdic.group6.core.player;

import ie.ucd.bdic.group6.core.card.Card;
import ie.ucd.bdic.group6.core.card.MoneyCard;
import ie.ucd.bdic.group6.core.common.OperationError;
import ie.ucd.bdic.group6.core.common.OperationResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BankPaymentSolverTest {

    @ParameterizedTest
    @CsvSource({"3, 3", "4, 5"})
    void minimalOverpaySelectsLowestSufficientValue(int due, int expectedSum) {
        List<Card> bank = List.of(
                new MoneyCard("m1", "1", 1),
                new MoneyCard("m2", "2", 2),
                new MoneyCard("m5", "5", 5));
        OperationResult<List<Card>> r = BankPaymentSolver.suggestMinimalOverpaySubset(bank, due);
        assertTrue(r.isSuccess());
        int sum = r.value().orElseThrow().stream().mapToInt(c -> c.bankValueM().orElseThrow()).sum();
        assertEquals(expectedSum, sum);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -5})
    void nonPositiveDueReturnsEmptyList(int due) {
        List<Card> bank = List.of(new MoneyCard("m1", "1", 1));
        OperationResult<List<Card>> r = BankPaymentSolver.suggestMinimalOverpaySubset(bank, due);
        assertTrue(r.isSuccess());
        assertTrue(r.value().orElseThrow().isEmpty());
    }

    @Test
    void insufficientBankReturnsFail() {
        List<Card> bank = List.of(
                new MoneyCard("m1", "1", 1),
                new MoneyCard("m2", "2", 2));
        OperationResult<List<Card>> r = BankPaymentSolver.suggestMinimalOverpaySubset(bank, 10);
        assertFalse(r.isSuccess());
        assertEquals(OperationError.INSUFFICIENT_BANK_VALUE,
                r.errorCode().orElseThrow());
    }

    @Test
    void emptyBankReturnsFail() {
        OperationResult<List<Card>> r = BankPaymentSolver.suggestMinimalOverpaySubset(List.of(), 5);
        assertFalse(r.isSuccess());
    }
}
