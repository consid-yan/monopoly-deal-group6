package ie.ucd.bdic.group6.core.rules;

import ie.ucd.bdic.group6.core.card.Card;
import ie.ucd.bdic.group6.core.common.OperationError;
import ie.ucd.bdic.group6.core.common.OperationResult;
import ie.ucd.bdic.group6.core.player.Bank;

import java.util.List;
import java.util.Objects;

/**
 * Payment validation for Monopoly Deal's no-change rule.
 */
public class PaymentRule {

    public OperationResult<Void> validateSelectedBankPayment(Bank bank, List<Card> selectedCards, int amountM) {
        Objects.requireNonNull(bank, "bank");
        Objects.requireNonNull(selectedCards, "selectedCards");
        if (amountM <= 0) {
            return OperationResult.ok();
        }
        if (selectedCards.isEmpty()) {
            return OperationResult.fail(
                    OperationError.INSUFFICIENT_BANK_VALUE,
                    "payment requires at least " + amountM + "M");
        }

        int selectedTotal = 0;
        for (Card card : selectedCards) {
            if (!bank.contains(card)) {
                return OperationResult.fail(OperationError.CARD_NOT_IN_BANK, "missing bank card: " + card.id());
            }
            if (card.bankValueM().isEmpty()) {
                return OperationResult.fail(
                        OperationError.CANNOT_BANK_RAINBOW_WILD,
                        "card has no bank value and cannot be used as bank payment: " + card.id());
            }
            selectedTotal += CardBankingRule.bankPaymentValueM(card);
        }
        if (selectedTotal < amountM) {
            return OperationResult.fail(
                    OperationError.INSUFFICIENT_BANK_VALUE,
                    "selected bank cards total " + selectedTotal + "M but require " + amountM + "M");
        }
        return OperationResult.ok();
    }
}
