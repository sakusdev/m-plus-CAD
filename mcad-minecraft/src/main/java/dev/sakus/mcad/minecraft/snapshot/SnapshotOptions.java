/*
 * SPDX-License-Identifier: MPL-2.0
 */
package dev.sakus.mcad.minecraft.snapshot;

import dev.sakus.mcad.api.CanonicalIdentifier;
import dev.sakus.mcad.api.MetadataValue;
import java.util.Collections;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Objects;
import java.util.TreeMap;

/** Immutable settings that affect logical snapshot content and identity. */
public record SnapshotOptions(
        boolean includeSourceWorldOrigin,
        boolean recordOmittedAirCount,
        NavigableMap<CanonicalIdentifier, MetadataValue> metadata) {
    private static final CanonicalIdentifier AIR_OMITTED =
            new CanonicalIdentifier("mcad", "air_omitted");
    private static final CanonicalIdentifier OMITTED_AIR_COUNT =
            new CanonicalIdentifier("mcad", "omitted_air_cells");

    public SnapshotOptions(
            boolean includeSourceWorldOrigin,
            boolean recordOmittedAirCount,
            Map<CanonicalIdentifier, MetadataValue> metadata) {
        this(includeSourceWorldOrigin, recordOmittedAirCount, immutableMetadata(metadata));
    }

    public SnapshotOptions {
        metadata = immutableMetadata(metadata);
    }

    public static SnapshotOptions defaults() {
        return new SnapshotOptions(true, true, Map.of());
    }

    private static NavigableMap<CanonicalIdentifier, MetadataValue> immutableMetadata(
            Map<CanonicalIdentifier, MetadataValue> values) {
        Objects.requireNonNull(values, "metadata");
        SnapshotMetadataValidator.validate(values, "metadata");
        if (values.containsKey(AIR_OMITTED) || values.containsKey(OMITTED_AIR_COUNT)) {
            throw new IllegalArgumentException("metadata uses an adapter-reserved key");
        }
        var copy = new TreeMap<CanonicalIdentifier, MetadataValue>();
        for (var entry : values.entrySet()) {
            copy.put(
                    Objects.requireNonNull(entry.getKey(), "metadata key"),
                    Objects.requireNonNull(entry.getValue(), "metadata value"));
        }
        return Collections.unmodifiableNavigableMap(copy);
    }
}
