/*
 * SPDX-License-Identifier: MPL-2.0
 */
package dev.sakus.mcad.api;


import java.util.Optional;

public record BoneDefinition(String stableId, String name, Optional<String> parentId, Transform localTransform) {
    public BoneDefinition {
        Checks.stableId(stableId, "stableId");
        Checks.nonBlank(name, "name");
        parentId = Checks.notNull(parentId, "parentId");
        parentId.ifPresent(value -> Checks.stableId(value, "parentId"));
        if (parentId.filter(stableId::equals).isPresent()) {
            throw new IllegalArgumentException("bone cannot parent itself");
        }
        Checks.notNull(localTransform, "localTransform");
    }
}
