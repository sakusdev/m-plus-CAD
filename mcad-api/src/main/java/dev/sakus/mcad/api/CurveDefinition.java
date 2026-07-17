/*
 * SPDX-License-Identifier: MPL-2.0
 */
package dev.sakus.mcad.api;


import java.util.List;

public record CurveDefinition(String stableId, String name, List<Vec3d> controlPoints, boolean closed) {
    public CurveDefinition {
        Checks.stableId(stableId, "stableId");
        Checks.nonBlank(name, "name");
        controlPoints = Checks.immutableList(controlPoints, "controlPoints");
        if (controlPoints.size() < 2) {
            throw new IllegalArgumentException("curve requires at least two control points");
        }
    }
}
