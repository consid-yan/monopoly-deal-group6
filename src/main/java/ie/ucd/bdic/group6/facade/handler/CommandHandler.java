package ie.ucd.bdic.group6.facade.handler;

import ie.ucd.bdic.group6.command.Command;
import ie.ucd.bdic.group6.facade.model.GameResult;

public interface CommandHandler<C extends Command> {
    Class<C> commandType();

    GameResult handle(C command);
}

