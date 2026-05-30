package ie.ucd.bdic.group6.command;

import java.util.List;
import java.util.Map;

public class PayDebtCommand extends AbstractCommand {
    public PayDebtCommand(Map<String, Object> payload) {
        super(payload);
    }

    @Override
    public CommandType getCommandType() {
        return CommandType.PAY_DEBT;
    }

    public String getDebtorId() {
        return (String) payload().get("debtorId");
    }

    public String getCreditorId() {
        return (String) payload().get("creditorId");
    }

    public String getDebtId() {
        return (String) payload().get("debtId");
    }

    @SuppressWarnings("unchecked")
    public List<String> getPaymentCardIds() {
        return (List<String>) payload().get("paymentCardIds");
    }
}
