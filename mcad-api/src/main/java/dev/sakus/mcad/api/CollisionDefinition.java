/*
 * SPDX-License-Identifier: MPL-2.0
 */
package dev.sakus.mcad.api;


import java.util.Comparator;
import java.util.List;

public record CollisionDefinition(
        String stableId,
        String name,
        CollisionKind kind,
        List<String> meshIds,
        List<SourceReference> sourceReferences) {

    public CollisionDefinition {
        Checks.stableId(stableId, "stableId");
        Checks.nonBlank(name, "name");
        Checks.notNull(kind, "kind");
        meshIds = Checks.immutableDistinctSortedStrings(meshIds, "meshIds");
        if (meshIds.isEmpty()) {
            throw new IllegalArgumentException("collision definition must reference at least one mesh");
        }
        sourceReferences = Checks.immutableDistinctSortedList(
                sourceReferences,
                Comparator.comparing(SourceReference::stableSortKey),
                "sourceReferences");
    }
}
