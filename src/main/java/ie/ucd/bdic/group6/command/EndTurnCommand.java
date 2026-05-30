package ie.ucd.bdic.group6.command;

import java.util.Map;

public class EndTurnCommand extends AbstractCommand {
    public EndTurnCommand(Map<String, Object> payload) {
        super(payload);
    }

    @Override
    public CommandType getCommandType() {
        return CommandType.END_TURN;
    }
}