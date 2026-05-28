package ie.ucd.bdic.group6.network.protocol;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class NetworkPayloads {
    public static Map<String, Object> normalizeMap(Map<String, Object> payload) {
        if (payload == null || payload.isEmpty()) {
            return new LinkedHashMap<>();
        }
        LinkedHashMap<String, Object> normalized = new LinkedHashMap<>();
        payload.forEach((key, value) -> normalized.put(String.valueOf(key), normalizeValue(value)));
        return normalized;
    }

    public static Object normalizeValue(Object value) {
        if (value == null
                || value instanceof String
                || value instanceof Number
                || value instanceof Boolean) {
            return value;
        }
        if (value instanceof Map<?, ?> map) {
            LinkedHashMap<String, Object> normalized = new LinkedHashMap<>();
            map.forEach((key, entryValue) -> normalized.put(String.valueOf(key), normalizeValue(entryValue)));
            return normalized;
        }
        if (value instanceof Iterable<?> iterable) {
            List<Object> normalized = new ArrayList<>();
            iterable.forEach(entry -> normalized.add(normalizeValue(entry)));
            return normalized;
        }
        if (value.getClass().isArray()) {
            List<Object> normalized = new ArrayList<>();
            int length = java.lang.reflect.Array.getLength(value);
            for (int index = 0; index < length; index++) {
                normalized.add(normalizeValue(java.lang.reflect.Array.get(value, index)));
            }
            return normalized;
        }
        if (value instanceof Enum<?> enumValue) {
            return enumValue.name();
        }
        if (value instanceof Serializable serializable) {
            return Objects.toString(serializable);
        }
        return String.valueOf(value);
    }
}
