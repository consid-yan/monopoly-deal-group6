package ie.ucd.bdic.group6.facade.handler;

import ie.ucd.bdic.group6.command.EndTurnCommand;
import ie.ucd.bdic.group6.facade.model.GameResult;

public class EndTurnHandler implements CommandHandler<EndTurnCommand> {
    @Override
    public Class<EndTurnCommand> commandType() {
        return EndTurnCommand.class;
    }

    @Override
    public GameResult handle(EndTurnCommand command) {
        return GameResult.notImplemented("END_TURN");
    }
}

