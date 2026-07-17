/*
 * SPDX-License-Identifier: MPL-2.0
 */
package dev.sakus.mcad.api;


import java.util.OptionalLong;

public record ExporterLimits(
        OptionalLong maximumVerticesPerPrimitive,
        OptionalLong maximumIndicesPerPrimitive,
        OptionalLong maximumMeshes,
        OptionalLong maximumMaterials) {

    public ExporterLimits {
        maximumVerticesPerPrimitive = positive(maximumVerticesPerPrimitive, "maximumVerticesPerPrimitive");
        maximumIndicesPerPrimitive = positive(maximumIndicesPerPrimitive, "maximumIndicesPerPrimitive");
        maximumMeshes = positive(maximumMeshes, "maximumMeshes");
        maximumMaterials = positive(maximumMaterials, "maximumMaterials");
    }

    public static ExporterLimits unlimited() {
        return new ExporterLimits(OptionalLong.empty(), OptionalLong.empty(), OptionalLong.empty(), OptionalLong.empty());
    }

    private static OptionalLong positive(OptionalLong value, String name) {
        Checks.notNull(value, name);
        if (value.isPresent()) {
            Checks.positive(value.getAsLong(), name);
        }
        return value;
    }
}
