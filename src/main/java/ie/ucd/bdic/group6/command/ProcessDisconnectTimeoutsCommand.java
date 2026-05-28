package ie.ucd.bdic.group6.command;

import java.util.Map;

public class ProcessDisconnectTimeoutsCommand extends AbstractCommand {
    public ProcessDisconnectTimeoutsCommand(Map<String, Object> payload) {
        super(payload);
    }

    @Override
    public CommandType getCommandType() {
        return CommandType.PROCESS_DISCONNECT_TIMEOUTS;
    }
}
