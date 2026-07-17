/*
 * SPDX-License-Identifier: MPL-2.0
 */
package dev.sakus.mcad.api;


import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Optional;
import java.util.TreeMap;

public record MeshPrimitive(
        List<Vec3d> positions,
        List<Vec3d> normals,
        List<Integer> indices,
        Optional<String> materialId,
        List<Color4d> vertexColours,
        NavigableMap<CanonicalIdentifier, List<Double>> customAttributes) {

    public MeshPrimitive(
            List<Vec3d> positions,
            List<Vec3d> normals,
            List<Integer> indices,
            Optional<String> materialId,
            List<Color4d> vertexColours,
            Map<CanonicalIdentifier, List<Double>> customAttributes) {
        this(positions, normals, indices, materialId, vertexColours, copyAttributes(customAttributes));
    }

    public MeshPrimitive {
        positions = Checks.immutableList(positions, "positions");
        normals = Checks.immutableList(normals, "normals");
        indices = Checks.immutableList(indices, "indices");
        materialId = Checks.notNull(materialId, "materialId");
        materialId.ifPresent(value -> Checks.stableId(value, "materialId"));
        vertexColours = Checks.immutableList(vertexColours, "vertexColours");
        customAttributes = copyAttributes(customAttributes);

        if (positions.isEmpty()) {
            throw new IllegalArgumentException("positions must not be empty");
        }
        if (normals.size() != positions.size()) {
            throw new IllegalArgumentException("normals must have the same count as positions");
        }
        if (indices.isEmpty() || indices.size() % 3 != 0) {
            throw new IllegalArgumentException("indices must contain complete triangles");
        }
        for (int index : indices) {
            if (index < 0 || index >= positions.size()) {
                throw new IllegalArgumentException("index out of bounds: " + index);
            }
        }
        if (!vertexColours.isEmpty() && vertexColours.size() != positions.size()) {
            throw new IllegalArgumentException("vertexColours must be empty or match positions");
        }
        for (var entry : customAttributes.entrySet()) {
            if (entry.getValue().size() != positions.size()) {
                throw new IllegalArgumentException("custom attribute must match positions: " + entry.getKey());
            }
        }
    }

    private static NavigableMap<CanonicalIdentifier, List<Double>> copyAttributes(
            Map<CanonicalIdentifier, List<Double>> values) {
        Checks.notNull(values, "customAttributes");
        var copy = new TreeMap<CanonicalIdentifier, List<Double>>();
        for (var entry : values.entrySet()) {
            var attribute = new ArrayList<Double>(Checks.notNull(entry.getValue(), "custom attribute"));
            for (double value : attribute) {
                Checks.finite(value, "custom attribute value");
            }
            copy.put(Checks.notNull(entry.getKey(), "custom attribute key"), List.copyOf(attribute));
        }
        return Checks.immutableSortedMap(copy, CanonicalIdentifier::compareTo, "customAttributes");
    }
}
