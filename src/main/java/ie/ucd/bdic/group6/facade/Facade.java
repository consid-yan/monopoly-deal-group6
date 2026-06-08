package ie.ucd.bdic.group6.facade;

import ie.ucd.bdic.group6.command.Command;
import ie.ucd.bdic.group6.facade.model.CommandResult;

public interface Facade {
    CommandResult execute(Command command);
}