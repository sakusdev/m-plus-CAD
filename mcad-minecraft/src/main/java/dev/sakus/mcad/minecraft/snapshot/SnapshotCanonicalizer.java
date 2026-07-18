/*
 * SPDX-License-Identifier: MPL-2.0
 */
package dev.sakus.mcad.minecraft.snapshot;

import dev.sakus.mcad.api.CanonicalIdentifier;
import java.util.Locale;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Objects;
import java.util.TreeMap;

final class SnapshotCanonicalizer {
    private static final int MAX_IDENTIFIER_LENGTH = 512;
    private static final int MAX_PROPERTY_COUNT = 256;
    private static final int MAX_PROPERTY_KEY_LENGTH = 128;
    private static final int MAX_PROPERTY_VALUE_LENGTH = 256;

    private SnapshotCanonicalizer() {
    }

    static CanonicalIdentifier blockIdentifier(String value) {
        String normalized = normalize(value, "block identifier", MAX_IDENTIFIER_LENGTH).toLowerCase(Locale.ROOT);
        return CanonicalIdentifier.parse(normalized);
    }

    static NavigableMap<String, String> stateProperties(Map<String, String> values) {
        Objects.requireNonNull(values, "stateProperties");
        if (values.size() > MAX_PROPERTY_COUNT) {
            throw new IllegalArgumentException(
                    "state property count exceeds limit: " + values.size() + " > " + MAX_PROPERTY_COUNT);
        }
        var normalized = new TreeMap<String, String>();
        for (var entry : values.entrySet()) {
            String key = normalize(entry.getKey(), "state property key", MAX_PROPERTY_KEY_LENGTH)
                    .toLowerCase(Locale.ROOT);
            String propertyValue = normalize(
                            entry.getValue(), "state property value", MAX_PROPERTY_VALUE_LENGTH)
                    .toLowerCase(Locale.ROOT);
            if (normalized.put(key, propertyValue) != null) {
                throw new IllegalArgumentException("duplicate state property after normalization: " + key);
            }
        }
        return normalized;
    }

    private static String normalize(String value, String name, int maxLength) {
        if (value == null) {
            throw new NullPointerException(name);
        }
        String normalized = value.trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        if (normalized.indexOf('\0') >= 0) {
            throw new IllegalArgumentException(name + " must not contain NUL");
        }
        if (normalized.length() > maxLength) {
            throw new IllegalArgumentException(name + " exceeds length limit: " + normalized.length());
        }
        return normalized;
    }
}
