package ie.ucd.bdic.group6.command;

import java.util.Map;

public record StartGameCommand(Map<String, Object> payload) implements Command {
    public StartGameCommand {
        payload = payload == null ? Map.of() : Map.copyOf(payload);
    }
}

