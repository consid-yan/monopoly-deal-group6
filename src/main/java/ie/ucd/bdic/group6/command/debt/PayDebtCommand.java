package ie.ucd.bdic.group6.command.debt;

import ie.ucd.bdic.group6.command.AbstractCommand;
import ie.ucd.bdic.group6.command.CommandPayload;
import ie.ucd.bdic.group6.command.CommandType;

import java.util.List;
import java.util.Map;

public class PayDebtCommand extends AbstractCommand {
    public PayDebtCommand(Map<String, Object> payload) {
        super(payload);
    }

    public PayDebtCommand(String sessionId, String playerId, String debtId, List<String> paymentCardIds) {
        this(payload(sessionId, playerId, debtId, paymentCardIds));
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

    private static Map<String, Object> payload(
            String sessionId,
            String playerId,
            String debtId,
            List<String> paymentCardIds) {
        Map<String, Object> payload = CommandPayload.session(sessionId, playerId);
        payload.put("debtId", debtId);
        payload.put("paymentCardIds", paymentCardIds);
        return payload;
    }
}
