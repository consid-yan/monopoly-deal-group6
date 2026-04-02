package ie.ucd.bdic.group6.command;

import java.util.Map;

public record TradeCommand(Map<String, Object> payload) implements Command {
    public TradeCommand {
        payload = payload == null ? Map.of() : Map.copyOf(payload);
    }
}

