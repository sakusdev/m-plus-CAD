/*
 * SPDX-License-Identifier: MPL-2.0
 */
package dev.sakus.mcad.api;


import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;

public record MeshGroup(
        String stableId,
        String name,
        List<MeshPrimitive> primitives,
        NavigableMap<CanonicalIdentifier, MetadataValue> customProperties,
        List<SourceReference> sourceReferences) {

    public MeshGroup(
            String stableId,
            String name,
            List<MeshPrimitive> primitives,
            Map<CanonicalIdentifier, MetadataValue> customProperties,
            List<SourceReference> sourceReferences) {
        this(stableId, name, primitives,
                Checks.immutableSortedMap(customProperties, CanonicalIdentifier::compareTo, "customProperties"),
                sourceReferences);
    }

    public MeshGroup {
        Checks.stableId(stableId, "stableId");
        Checks.nonBlank(name, "name");
        primitives = Checks.immutableList(primitives, "primitives");
        if (primitives.isEmpty()) {
            throw new IllegalArgumentException("mesh group must contain at least one primitive");
        }
        customProperties = Checks.immutableSortedMap(
                customProperties, CanonicalIdentifier::compareTo, "customProperties");
        sourceReferences = Checks.immutableDistinctSortedList(
                sourceReferences,
                Comparator.comparing(SourceReference::stableSortKey),
                "sourceReferences");
    }
}
