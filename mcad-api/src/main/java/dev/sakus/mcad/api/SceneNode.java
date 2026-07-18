/*
 * SPDX-License-Identifier: MPL-2.0
 */
package dev.sakus.mcad.api;


import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Optional;

public record SceneNode(
        String stableId,
        String name,
        Optional<String> parentId,
        Transform localTransform,
        List<String> meshIds,
        List<String> childIds,
        NavigableMap<CanonicalIdentifier, MetadataValue> customProperties,
        List<SourceReference> sourceReferences) {

    public SceneNode(
            String stableId,
            String name,
            Optional<String> parentId,
            Transform localTransform,
            List<String> meshIds,
            List<String> childIds,
            Map<CanonicalIdentifier, MetadataValue> customProperties,
            List<SourceReference> sourceReferences) {
        this(stableId, name, parentId, localTransform, meshIds, childIds,
                Checks.immutableSortedMap(customProperties, CanonicalIdentifier::compareTo, "customProperties"),
                sourceReferences);
    }

    public SceneNode {
        Checks.stableId(stableId, "stableId");
        Checks.nonBlank(name, "name");
        parentId = Checks.notNull(parentId, "parentId");
        parentId.ifPresent(value -> Checks.stableId(value, "parentId"));
        if (parentId.filter(stableId::equals).isPresent()) {
            throw new IllegalArgumentException("node cannot parent itself");
        }
        Checks.notNull(localTransform, "localTransform");
        meshIds = Checks.immutableDistinctSortedStrings(meshIds, "meshIds");
        childIds = Checks.immutableDistinctSortedStrings(childIds, "childIds");
        if (childIds.contains(stableId)) {
            throw new IllegalArgumentException("node cannot contain itself as a child");
        }
        customProperties = Checks.immutableSortedMap(
                customProperties, CanonicalIdentifier::compareTo, "customProperties");
        sourceReferences = Checks.immutableDistinctSortedList(
                sourceReferences,
                Comparator.comparing(SourceReference::stableSortKey),
                "sourceReferences");
    }
}
