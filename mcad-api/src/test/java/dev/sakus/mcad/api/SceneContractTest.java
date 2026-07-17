/*
 * SPDX-License-Identifier: MPL-2.0
 */
package dev.sakus.mcad.api;


import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import org.junit.jupiter.api.Test;

class SceneContractTest {
    private static MeshPrimitive triangle(Optional<String> materialId) {
        return new MeshPrimitive(
                List.of(new Vec3d(0, 0, 0), new Vec3d(1, 0, 0), new Vec3d(0, 1, 0)),
                List.of(new Vec3d(0, 0, 1), new Vec3d(0, 0, 1), new Vec3d(0, 0, 1)),
                List.of(0, 1, 2), materialId, List.of(), Map.of());
    }

    @Test
    void primitiveRejectsInvalidNumbersAndIndices() {
        assertThrows(IllegalArgumentException.class, () -> new Vec3d(Double.NaN, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new MeshPrimitive(
                List.of(new Vec3d(0, 0, 0), new Vec3d(1, 0, 0), new Vec3d(0, 1, 0)),
                List.of(new Vec3d(0, 0, 1), new Vec3d(0, 0, 1), new Vec3d(0, 0, 1)),
                List.of(0, 1, 3), Optional.empty(), List.of(), Map.of()));
    }

    @Test
    void generatedSceneSortsCollectionsAndValidatesReferences() {
        var material = new MaterialDefinition(
                "material:stone", "Stone", new Color4d(0.5, 0.5, 0.5, 1), 0, 1,
                Color3d.BLACK, 0, AlphaMode.OPAQUE, OptionalDouble.empty(), List.of(), Map.of());
        var mesh = new MeshGroup("mesh:stone", "Stone", List.of(triangle(Optional.of(material.stableId()))), Map.of());
        var child = new SceneNode("node:child", "Child", Optional.of("node:root"), Transform.IDENTITY,
                List.of("mesh:stone"), List.of(), Map.of(), List.of());
        var root = new SceneNode("node:root", "Root", Optional.empty(), Transform.IDENTITY,
                List.of(), List.of("node:child"), Map.of(), List.of());

        var scene = new GeneratedScene(
                ApiVersions.GENERATED_SCENE, "scene:test", List.of("node:root"), List.of(child, root),
                List.of(mesh), List.of(material), List.of(), List.of(), List.of(), List.of(), List.of(),
                Map.of(), List.of(), new SceneStatistics(1, 1, 0, 3, 1, 1, 1));

        assertEquals(List.of("node:child", "node:root"), scene.nodes().stream().map(SceneNode::stableId).toList());
        assertThrows(UnsupportedOperationException.class, () -> scene.nodes().clear());
    }

    @Test
    void generatedSceneRejectsInconsistentHierarchyAndUnknownMaterial() {
        var root = new SceneNode("node:root", "Root", Optional.empty(), Transform.IDENTITY,
                List.of(), List.of("node:missing"), Map.of(), List.of());
        assertThrows(IllegalArgumentException.class, () -> new GeneratedScene(
                ApiVersions.GENERATED_SCENE, "scene:bad", List.of("node:root"), List.of(root),
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                Map.of(), List.of(), new SceneStatistics(0, 0, 0, 0, 0, 0, 0)));

        var mesh = new MeshGroup("mesh:test", "Test", List.of(triangle(Optional.of("material:missing"))), Map.of());
        var meshNode = new SceneNode("node:mesh", "Mesh", Optional.empty(), Transform.IDENTITY,
                List.of("mesh:test"), List.of(), Map.of(), List.of());
        assertThrows(IllegalArgumentException.class, () -> new GeneratedScene(
                ApiVersions.GENERATED_SCENE, "scene:bad-material", List.of("node:mesh"), List.of(meshNode),
                List.of(mesh), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                Map.of(), List.of(), new SceneStatistics(0, 0, 0, 3, 1, 1, 0)));
    }
}
