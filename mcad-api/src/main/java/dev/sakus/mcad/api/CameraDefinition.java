/*
 * SPDX-License-Identifier: MPL-2.0
 */
package dev.sakus.mcad.api;


import java.util.Comparator;
import java.util.List;
import java.util.OptionalDouble;

public record CameraDefinition(
        String stableId,
        String name,
        Transform transform,
        CameraProjection projection,
        double nearPlane,
        OptionalDouble farPlane,
        OptionalDouble verticalFieldOfViewRadians,
        OptionalDouble orthographicHeight,
        List<SourceReference> sourceReferences) {

    public CameraDefinition {
        Checks.stableId(stableId, "stableId");
        Checks.nonBlank(name, "name");
        Checks.notNull(transform, "transform");
        Checks.notNull(projection, "projection");
        Checks.finite(nearPlane, "nearPlane");
        if (nearPlane <= 0.0) {
            throw new IllegalArgumentException("nearPlane must be positive");
        }
        farPlane = Checks.notNull(farPlane, "farPlane");
        verticalFieldOfViewRadians = Checks.notNull(verticalFieldOfViewRadians, "verticalFieldOfViewRadians");
        orthographicHeight = Checks.notNull(orthographicHeight, "orthographicHeight");
        farPlane.ifPresent(value -> {
            Checks.finite(value, "farPlane");
            if (value <= nearPlane) {
                throw new IllegalArgumentException("farPlane must be greater than nearPlane");
            }
        });
        if (projection == CameraProjection.PERSPECTIVE) {
            if (verticalFieldOfViewRadians.isEmpty() || orthographicHeight.isPresent()) {
                throw new IllegalArgumentException("perspective camera requires FOV and no orthographic height");
            }
            double fieldOfView = Checks.finite(verticalFieldOfViewRadians.getAsDouble(), "verticalFieldOfViewRadians");
            if (fieldOfView <= 0.0 || fieldOfView >= Math.PI) {
                throw new IllegalArgumentException("verticalFieldOfViewRadians must be between 0 and PI");
            }
        } else {
            if (orthographicHeight.isEmpty() || verticalFieldOfViewRadians.isPresent()) {
                throw new IllegalArgumentException("orthographic camera requires height and no FOV");
            }
            Checks.finite(orthographicHeight.getAsDouble(), "orthographicHeight");
            if (orthographicHeight.getAsDouble() <= 0.0) {
                throw new IllegalArgumentException("orthographicHeight must be positive");
            }
        }
        sourceReferences = Checks.immutableDistinctSortedList(
                sourceReferences,
                Comparator.comparing(SourceReference::stableSortKey),
                "sourceReferences");
    }
}
