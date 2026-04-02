package ie.ucd.bdic.group6.command;

import java.util.Map;

public record EndTurnCommand(Map<String, Object> payload) implements Command {
    public EndTurnCommand {
        payload = payload == null ? Map.of() : Map.copyOf(payload);
    }
}

