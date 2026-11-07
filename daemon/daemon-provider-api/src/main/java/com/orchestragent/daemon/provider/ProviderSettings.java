package com.orchestragent.daemon.provider;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The settings of one provider: the object under the provider's ID in the daemon's {@code config.json}, for example
 * {@code "claude": {"permissionMode": "acceptEdits"}}. Values are JSON values as Java objects (strings, numbers,
 * booleans, lists, maps). A wrong value is an {@link IllegalArgumentException} with a message for the user.
 */
public record ProviderSettings(String providerId, Map<String, Object> values) {

    public ProviderSettings {
        values = values == null ? Map.of() : Map.copyOf(values);
    }

    public static ProviderSettings empty(String providerId) {
        return new ProviderSettings(providerId, Map.of());
    }

    /** A string setting; blank counts as missing. */
    public Optional<String> string(String key) {
        Object value = values.get(key);
        if (value == null) {
            return Optional.empty();
        }
        if (!(value instanceof String text)) {
            throw wrong(key, "a string");
        }
        return text.isBlank() ? Optional.empty() : Optional.of(text.strip());
    }

    /** A list of strings; a single string counts as a list of one. */
    public List<String> strings(String key) {
        Object value = values.get(key);
        if (value == null) {
            return List.of();
        }
        if (value instanceof String text) {
            return text.isBlank() ? List.of() : List.of(text.strip());
        }
        if (!(value instanceof List<?> list)) {
            throw wrong(key, "a list of strings");
        }
        List<String> result = new ArrayList<>();
        for (Object item : list) {
            if (!(item instanceof String text)) {
                throw wrong(key, "a list of strings");
            }
            if (!text.isBlank()) {
                result.add(text.strip());
            }
        }
        return List.copyOf(result);
    }

    /** A whole number setting. */
    public Optional<Long> number(String key) {
        Object value = values.get(key);
        if (value == null) {
            return Optional.empty();
        }
        if (value instanceof Integer || value instanceof Long || value instanceof Short) {
            return Optional.of(((Number) value).longValue());
        }
        throw wrong(key, "a whole number");
    }

    private IllegalArgumentException wrong(String key, String expected) {
        return new IllegalArgumentException("Setting " + providerId + "." + key + " in config.json must be " + expected);
    }
}
