package ie.ucd.bdic.group6.command;

import java.util.LinkedHashMap;
import java.util.Map;

public final class CommandPayload {

    public static Map<String, Object> session(String sessionId, String playerId) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("sessionId", sessionId);
        payload.put("playerId", playerId);
        return payload;
    }
}
