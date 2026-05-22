package ie.ucd.bdic.group6.facade.handler;

import ie.ucd.bdic.group6.command.Command;
import ie.ucd.bdic.group6.core.engine.ActionExecutor;
import ie.ucd.bdic.group6.exception.GameException;
import ie.ucd.bdic.group6.facade.model.GameResult;

public abstract class AbstractCommandHandler<C extends Command> implements CommandHandler<C> {
    protected final ActionExecutor actionExecutor;

    protected AbstractCommandHandler(ActionExecutor actionExecutor) {
        this.actionExecutor = actionExecutor;
    }

    @Override
    public GameResult handle(C command) {
        try {
            return execute(command);
        } catch (GameException e) {
            return GameResult.failure(e.getErrorCode(), e.getMessage());
        } catch (Exception e) {
            return GameResult.failure("INTERNAL_ERROR",
                    "Failed to execute " + command.getCommandType() + ": " + e.getMessage());
        }
    }

    protected abstract GameResult execute(C command);
}
