package ie.ucd.bdic.group6.facade.handler;

import ie.ucd.bdic.group6.command.StartGameCommand;
import ie.ucd.bdic.group6.facade.model.GameResult;

public class StartGameHandler implements CommandHandler<StartGameCommand> {
    @Override
    public Class<StartGameCommand> commandType() {
        return StartGameCommand.class;
    }

    @Override
    public GameResult handle(StartGameCommand command) {
        return GameResult.notImplemented("START_GAME");
    }
}

