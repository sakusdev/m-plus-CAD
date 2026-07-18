/*
 * SPDX-License-Identifier: MPL-2.0
 */
package dev.sakus.mcad.minecraft.snapshot;

import dev.sakus.mcad.api.CanonicalIdentifier;
import dev.sakus.mcad.api.MetadataValue;
import java.util.Collections;
import java.util.Comparator;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;

/** Neutral values copied from one Minecraft block state on the game thread. */
public record SnapshotBlockData(
        Optional<String> blockIdentifier,
        NavigableMap<String, String> stateProperties,
        NavigableMap<CanonicalIdentifier, MetadataValue> metadata) {

    public SnapshotBlockData(
            Optional<String> blockIdentifier,
            Map<String, String> stateProperties,
            Map<CanonicalIdentifier, MetadataValue> metadata) {
        this(blockIdentifier, immutableStrings(stateProperties), immutableMetadata(metadata));
    }

    public SnapshotBlockData {
        blockIdentifier = Objects.requireNonNull(blockIdentifier, "blockIdentifier");
        stateProperties = immutableStrings(stateProperties);
        metadata = immutableMetadata(metadata);
        if (blockIdentifier.isEmpty() && (!stateProperties.isEmpty() || !metadata.isEmpty())) {
            throw new IllegalArgumentException("air cells must not carry block state or metadata");
        }
    }

    public static SnapshotBlockData air() {
        return new SnapshotBlockData(Optional.empty(), Map.of(), Map.of());
    }

    public static SnapshotBlockData block(
            String blockIdentifier,
            Map<String, String> stateProperties,
            Map<CanonicalIdentifier, MetadataValue> metadata) {
        return new SnapshotBlockData(
                Optional.of(Objects.requireNonNull(blockIdentifier, "blockIdentifier")),
                stateProperties,
                metadata);
    }

    public boolean isAir() {
        return blockIdentifier.isEmpty();
    }

    private static NavigableMap<String, String> immutableStrings(Map<String, String> values) {
        Objects.requireNonNull(values, "stateProperties");
        var copy = new TreeMap<String, String>(Comparator.naturalOrder());
        for (var entry : values.entrySet()) {
            copy.put(
                    Objects.requireNonNull(entry.getKey(), "state property key"),
                    Objects.requireNonNull(entry.getValue(), "state property value"));
        }
        return Collections.unmodifiableNavigableMap(copy);
    }

    private static NavigableMap<CanonicalIdentifier, MetadataValue> immutableMetadata(
            Map<CanonicalIdentifier, MetadataValue> values) {
        Objects.requireNonNull(values, "metadata");
        SnapshotMetadataValidator.validate(values, "metadata");
        var copy = new TreeMap<CanonicalIdentifier, MetadataValue>();
        for (var entry : values.entrySet()) {
            copy.put(
                    Objects.requireNonNull(entry.getKey(), "metadata key"),
                    Objects.requireNonNull(entry.getValue(), "metadata value"));
        }
        return Collections.unmodifiableNavigableMap(copy);
    }
}
