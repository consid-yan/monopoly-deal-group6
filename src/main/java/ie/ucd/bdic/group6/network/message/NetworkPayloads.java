package ie.ucd.bdic.group6.network.message;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class NetworkPayloads {

    public static Map<String, Object> normalizeMap(Map<String, Object> payload) {
        if (payload == null || payload.isEmpty()) {
            return new LinkedHashMap<>();
        }

        LinkedHashMap<String, Object> normalized = new LinkedHashMap<>();

        payload.forEach((key, value) -> {
            if (key == null) {
                throw new IllegalArgumentException("payload key must not be null");
            }

            normalized.put(key, normalizeValue(value));
        });

        return normalized;
    }

    public static Object normalizeValue(Object value) {
        if (value == null
                || value instanceof String
                || value instanceof Number
                || value instanceof Boolean) {
            return value;
        }

        switch (value) {
            case Enum<?> enumValue -> {
                return enumValue.name();
            }
            case Map<?, ?> map -> {
                LinkedHashMap<String, Object> normalized = new LinkedHashMap<>();

                map.forEach((key, entryValue) -> {
                    if (key == null) {
                        throw new IllegalArgumentException("payload map key must not be null");
                    }

                    normalized.put(String.valueOf(key), normalizeValue(entryValue));
                });

                return normalized;
            }
            case Iterable<?> iterable -> {
                List<Object> normalized = new ArrayList<>();
                iterable.forEach(entry -> normalized.add(normalizeValue(entry)));
                return normalized;
            }
            default -> {
            }
        }

        if (value.getClass().isArray()) {
            List<Object> normalized = new ArrayList<>();
            int length = java.lang.reflect.Array.getLength(value);

            for (int index = 0; index < length; index++) {
                normalized.add(normalizeValue(java.lang.reflect.Array.get(value, index)));
            }

            return normalized;
        }

        throw new IllegalArgumentException(
                "Unsupported payload value type: " + value.getClass().getName()
        );
    }
}