package ie.ucd.bdic.group6.command.game;

import ie.ucd.bdic.group6.command.AbstractCommand;
import ie.ucd.bdic.group6.command.CommandPayload;
import ie.ucd.bdic.group6.command.CommandType;

import java.util.Map;

public class EndTurnCommand extends AbstractCommand {
    public EndTurnCommand(Map<String, Object> payload) {
        super(payload);
    }

    public EndTurnCommand(String sessionId, String playerId) {
        super(CommandPayload.session(sessionId, playerId));
    }

    @Override
    public CommandType getCommandType() {
        return CommandType.END_TURN;
    }
}
