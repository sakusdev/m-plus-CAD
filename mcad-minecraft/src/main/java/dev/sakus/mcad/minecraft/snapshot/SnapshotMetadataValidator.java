/*
 * SPDX-License-Identifier: MPL-2.0
 */
package dev.sakus.mcad.minecraft.snapshot;

import dev.sakus.mcad.api.CanonicalIdentifier;
import dev.sakus.mcad.api.MetadataValue;
import java.util.Map;
import java.util.Objects;

final class SnapshotMetadataValidator {
    private static final int MAX_ROOT_ENTRIES = 256;
    private static final int MAX_MAP_ENTRIES = 256;
    private static final int MAX_LIST_ENTRIES = 1_024;
    private static final int MAX_DEPTH = 16;
    private static final int MAX_TOTAL_VALUES = 4_096;
    private static final int MAX_STRING_LENGTH = 65_536;

    private SnapshotMetadataValidator() {
    }

    static void validate(Map<CanonicalIdentifier, MetadataValue> metadata, String name) {
        Objects.requireNonNull(metadata, name);
        if (metadata.size() > MAX_ROOT_ENTRIES) {
            throw new IllegalArgumentException(
                    name + " entry count exceeds limit: " + metadata.size() + " > " + MAX_ROOT_ENTRIES);
        }
        var count = new Counter();
        for (var entry : metadata.entrySet()) {
            Objects.requireNonNull(entry.getKey(), name + " key");
            validateValue(Objects.requireNonNull(entry.getValue(), name + " value"), 1, count, name);
        }
    }

    private static void validateValue(MetadataValue value, int depth, Counter count, String name) {
        if (depth > MAX_DEPTH) {
            throw new IllegalArgumentException(name + " nesting exceeds limit: " + MAX_DEPTH);
        }
        count.increment(name);
        switch (value) {
            case MetadataValue.StringValue stringValue -> {
                if (stringValue.value().length() > MAX_STRING_LENGTH) {
                    throw new IllegalArgumentException(
                            name + " string exceeds length limit: " + stringValue.value().length());
                }
            }
            case MetadataValue.ListValue listValue -> {
                if (listValue.values().size() > MAX_LIST_ENTRIES) {
                    throw new IllegalArgumentException(
                            name + " list exceeds entry limit: " + listValue.values().size());
                }
                for (MetadataValue child : listValue.values()) {
                    validateValue(child, depth + 1, count, name);
                }
            }
            case MetadataValue.MapValue mapValue -> {
                if (mapValue.values().size() > MAX_MAP_ENTRIES) {
                    throw new IllegalArgumentException(
                            name + " map exceeds entry limit: " + mapValue.values().size());
                }
                for (var child : mapValue.values().entrySet()) {
                    Objects.requireNonNull(child.getKey(), name + " nested key");
                    validateValue(Objects.requireNonNull(child.getValue(), name + " nested value"),
                            depth + 1, count, name);
                }
            }
            case MetadataValue.LongValue ignored -> {
                // Already validated by the neutral API type.
            }
            case MetadataValue.DoubleValue ignored -> {
                // Already validated by the neutral API type.
            }
            case MetadataValue.BooleanValue ignored -> {
                // Already validated by the neutral API type.
            }
        }
    }

    private static final class Counter {
        private int value;

        void increment(String name) {
            value++;
            if (value > MAX_TOTAL_VALUES) {
                throw new IllegalArgumentException(
                        name + " total value count exceeds limit: " + MAX_TOTAL_VALUES);
            }
        }
    }
}
