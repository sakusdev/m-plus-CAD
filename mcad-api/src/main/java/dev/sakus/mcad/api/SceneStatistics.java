/*
 * SPDX-License-Identifier: MPL-2.0
 */
package dev.sakus.mcad.api;


public record SceneStatistics(
        long sourceBlockCount,
        long visibleFaceCount,
        long removedHiddenFaceCount,
        long vertexCount,
        long triangleCount,
        long meshCount,
        long materialCount) {

    public SceneStatistics {
        Checks.nonNegative(sourceBlockCount, "sourceBlockCount");
        Checks.nonNegative(visibleFaceCount, "visibleFaceCount");
        Checks.nonNegative(removedHiddenFaceCount, "removedHiddenFaceCount");
        Checks.nonNegative(vertexCount, "vertexCount");
        Checks.nonNegative(triangleCount, "triangleCount");
        Checks.nonNegative(meshCount, "meshCount");
        Checks.nonNegative(materialCount, "materialCount");
    }
}
