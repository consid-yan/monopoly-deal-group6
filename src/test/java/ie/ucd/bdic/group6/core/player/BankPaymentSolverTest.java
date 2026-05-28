package ie.ucd.bdic.group6.core.player;

import ie.ucd.bdic.group6.core.card.Card;
import ie.ucd.bdic.group6.core.card.MoneyCard;
import ie.ucd.bdic.group6.core.common.OperationError;
import ie.ucd.bdic.group6.core.common.OperationResult;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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

    @Test
    void zeroDueReturnsEmptyList() {
        List<Card> bank = List.of(new MoneyCard("m1", "1", 1));
        OperationResult<List<Card>> r = BankPaymentSolver.suggestMinimalOverpaySubset(bank, 0);
        assertTrue(r.isSuccess());
        assertTrue(r.value().orElseThrow().isEmpty());
    }

    @Test
    void negativeDueReturnsEmptyList() {
        List<Card> bank = List.of(new MoneyCard("m1", "1", 1));
        OperationResult<List<Card>> r = BankPaymentSolver.suggestMinimalOverpaySubset(bank, -5);
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
