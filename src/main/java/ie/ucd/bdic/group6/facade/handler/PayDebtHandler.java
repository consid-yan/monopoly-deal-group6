// PayDebtHandler.java
package ie.ucd.bdic.group6.facade.handler;

import ie.ucd.bdic.group6.command.debt.PayDebtCommand;
import ie.ucd.bdic.group6.core.engine.ActionExecutor;
import ie.ucd.bdic.group6.facade.model.CommandResult;

public class PayDebtHandler extends AbstractCommandHandler<PayDebtCommand> {
    public PayDebtHandler(ActionExecutor actionExecutor) {
        super(actionExecutor);
    }

    @Override
    public Class<PayDebtCommand> commandType() {
        return PayDebtCommand.class;
    }

    @Override
    protected CommandResult execute(PayDebtCommand command) {
        return CommandResult.success(actionExecutor.executePayDebt(command));
    }
}
