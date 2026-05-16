package ie.ucd.bdic.group6.facade;

import ie.ucd.bdic.group6.command.Command;
import ie.ucd.bdic.group6.facade.model.GameResult;

public interface Facade {
    GameResult execute(Command command);
}