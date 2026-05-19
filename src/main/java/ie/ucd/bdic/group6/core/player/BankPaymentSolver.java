package ie.ucd.bdic.group6.core.player;

import ie.ucd.bdic.group6.core.card.Card;
import ie.ucd.bdic.group6.core.card.CardBankSupport;
import ie.ucd.bdic.group6.core.common.OperationError;
import ie.ucd.bdic.group6.core.common.OperationResult;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Brute-force bank payment planning (N is small in Monopoly Deal). Chooses a subset of bank cards whose total
 * value is &gt;= {@code dueM} with minimal total (no change rule: payer may overpay).
 */
public final class BankPaymentSolver {

    private BankPaymentSolver() {
    }

    public static int totalBankPaymentValue(List<Card> bankCards) {
        return bankCards.stream().mapToInt(CardBankSupport::bankPaymentValueM).sum();
    }

    public static boolean canCoverWithBank(List<Card> bankCards, int dueM) {
        return totalBankPaymentValue(bankCards) >= dueM;
    }

    /**
     * Finds a subset of {@code bankCards} with minimal sum {@code >= dueM}. Fails if impossible.
     */
    public static OperationResult<List<Card>> suggestMinimalOverpaySubset(List<Card> bankCards, int dueM) {
        Objects.requireNonNull(bankCards, "bankCards");
        if (dueM <= 0) {
            return OperationResult.ok(List.of());
        }
        List<Card> usable = bankCards.stream().filter(c -> c.bankValueM().isPresent()).toList();
        if (totalBankPaymentValue(usable) < dueM) {
            return OperationResult.fail(
                    OperationError.INSUFFICIENT_BANK_VALUE,
                    "bank total " + totalBankPaymentValue(usable) + "M is less than due " + dueM + "M");
        }
        int n = usable.size();
        int bestSum = Integer.MAX_VALUE;
        List<Card> best = null;
        for (int mask = 1; mask < (1 << n); mask++) {
            int sum = 0;
            List<Card> chosen = new ArrayList<>();
            for (int i = 0; i < n; i++) {
                if ((mask & (1 << i)) != 0) {
                    Card c = usable.get(i);
                    sum += CardBankSupport.bankPaymentValueM(c);
                    chosen.add(c);
                }
            }
            if (sum >= dueM && sum < bestSum) {
                bestSum = sum;
                best = chosen;
            }
        }
        if (best == null) {
            return OperationResult.fail(
                    OperationError.NO_BANK_CARD_MEETS_MINIMUM_PAYMENT, "no subset covers the payment");
        }
        best.sort(Comparator.comparingInt(c -> c.bankValueM().orElseThrow()));
        return OperationResult.ok(best);
    }
}
