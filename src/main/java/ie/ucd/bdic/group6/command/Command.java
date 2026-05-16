package ie.ucd.bdic.group6.command;

import java.util.Map;

public interface Command {
    CommandType getCommandType();
    Map<String, Object> payload();
}