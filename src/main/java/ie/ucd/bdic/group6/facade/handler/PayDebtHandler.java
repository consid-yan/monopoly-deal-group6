// PayDebtHandler.java
package ie.ucd.bdic.group6.facade.handler;

import ie.ucd.bdic.group6.command.PayDebtCommand;
import ie.ucd.bdic.group6.core.engine.ActionExecutor;
import ie.ucd.bdic.group6.facade.model.GameResult;

public class PayDebtHandler extends AbstractCommandHandler<PayDebtCommand> {
    public PayDebtHandler(ActionExecutor actionExecutor) {
        super(actionExecutor);
    }

    @Override
    public Class<PayDebtCommand> commandType() {
        return PayDebtCommand.class;
    }

    @Override
    protected GameResult execute(PayDebtCommand command) {
        return GameResult.success(actionExecutor.executePayDebt(command));
    }
}
