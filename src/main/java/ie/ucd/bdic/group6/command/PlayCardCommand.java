package ie.ucd.bdic.group6.command;

import java.util.Map;

public record PlayCardCommand(Map<String, Object> payload) implements Command {
    public PlayCardCommand {
        payload = payload == null ? Map.of() : Map.copyOf(payload);
    }
}

