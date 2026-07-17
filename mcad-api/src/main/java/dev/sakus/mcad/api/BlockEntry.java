/*
 * SPDX-License-Identifier: MPL-2.0
 */
package dev.sakus.mcad.api;


import java.util.Comparator;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;

public record BlockEntry(
        BlockPosition relativePosition,
        CanonicalIdentifier blockId,
        NavigableMap<String, String> stateProperties,
        NavigableMap<CanonicalIdentifier, MetadataValue> metadata) {

    public BlockEntry(
            BlockPosition relativePosition,
            CanonicalIdentifier blockId,
            Map<String, String> stateProperties,
            Map<CanonicalIdentifier, MetadataValue> metadata) {
        this(
                relativePosition,
                blockId,
                copyStateProperties(stateProperties),
                Checks.immutableSortedMap(metadata, CanonicalIdentifier::compareTo, "metadata"));
    }

    public BlockEntry {
        Checks.notNull(relativePosition, "relativePosition");
        Checks.notNull(blockId, "blockId");
        stateProperties = copyStateProperties(stateProperties);
        metadata = Checks.immutableSortedMap(metadata, CanonicalIdentifier::compareTo, "metadata");
    }

    private static NavigableMap<String, String> copyStateProperties(Map<String, String> values) {
        Checks.notNull(values, "stateProperties");
        var checked = new TreeMap<String, String>(Comparator.naturalOrder());
        for (var entry : values.entrySet()) {
            String key = Checks.nonBlank(entry.getKey(), "state property key");
            String value = Checks.nonBlank(entry.getValue(), "state property value");
            checked.put(key, value);
        }
        return Checks.immutableSortedMap(checked, Comparator.naturalOrder(), "stateProperties");
    }
}
