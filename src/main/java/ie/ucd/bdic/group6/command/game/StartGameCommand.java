package ie.ucd.bdic.group6.command.game;

import ie.ucd.bdic.group6.command.AbstractCommand;
import ie.ucd.bdic.group6.command.CommandType;

import java.util.Map;

public class StartGameCommand extends AbstractCommand {
    public StartGameCommand(Map<String, Object> payload) {
        super(payload);
    }

    @Override
    public CommandType getCommandType() {
        return CommandType.START_GAME;
    }
}
