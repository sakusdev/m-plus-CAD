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
                Color3d.BLACK, 0, AlphaMode.OPAQUE, OptionalDouble.empty(), List.of(), Map.of(), List.of());
        var mesh = new MeshGroup(
                "mesh:stone", "Stone", List.of(triangle(Optional.of(material.stableId()))), Map.of(), List.of());
        var child = new SceneNode("node:child", "Child", Optional.of("node:root"), Transform.IDENTITY,
                List.of("mesh:stone"), List.of(), Map.of(), List.of());
        var root = new SceneNode("node:root", "Root", Optional.empty(), Transform.IDENTITY,
                List.of(), List.of("node:child"), Map.of(), List.of());

        var scene = new GeneratedScene(
                ApiVersions.GENERATED_SCENE, "scene:test", Transform.IDENTITY,
                List.of("node:root"), List.of(child, root), List.of(mesh), List.of(material),
                List.of(), List.of(), List.of(), List.of(), List.of(),
                Map.of(), List.of(), new SceneStatistics(1, 1, 0, 3, 1, 1, 1));

        assertEquals(List.of("node:child", "node:root"), scene.nodes().stream().map(SceneNode::stableId).toList());
        assertEquals(Transform.IDENTITY, scene.originTransform());
        assertThrows(UnsupportedOperationException.class, () -> scene.nodes().clear());
    }

    @Test
    void generatedSceneCarriesPlacedElementsAndSourceReferences() {
        var source = new SourceReference(
                "snapshot:test",
                Optional.of(new BlockPosition(1, 2, 3)),
                Optional.of(CanonicalIdentifier.parse("minecraft:stone")),
                Optional.of(CanonicalIdentifier.parse("mcad:light-marker")));
        var lightTransform = new Transform(new Vec3d(1.5, 2.5, 3.5), Quaterniond.IDENTITY, Vec3d.ONE);
        var cameraTransform = new Transform(new Vec3d(4.0, 5.0, 6.0), Quaterniond.IDENTITY, Vec3d.ONE);
        var light = new LightDefinition(
                "light:key", "Key", lightTransform, LightType.POINT, new Color3d(1, 1, 1), 10,
                OptionalDouble.of(8), OptionalDouble.empty(), OptionalDouble.empty(), List.of(source));
        var camera = new CameraDefinition(
                "camera:main", "Main", cameraTransform, CameraProjection.PERSPECTIVE, 0.1,
                OptionalDouble.of(1000), OptionalDouble.of(1.0), OptionalDouble.empty(), List.of(source));
        var origin = new Transform(new Vec3d(-2, 0, -2), Quaterniond.IDENTITY, Vec3d.ONE);

        var scene = new GeneratedScene(
                ApiVersions.GENERATED_SCENE, "scene:placed", origin,
                List.of(), List.of(), List.of(), List.of(), List.of(light), List.of(camera),
                List.of(), List.of(), List.of(), Map.of(), List.of(),
                new SceneStatistics(0, 0, 0, 0, 0, 0, 0));

        assertEquals(origin, scene.originTransform());
        assertEquals(lightTransform, scene.lights().getFirst().transform());
        assertEquals(cameraTransform, scene.cameras().getFirst().transform());
        assertEquals(source, scene.lights().getFirst().sourceReferences().getFirst());
    }

    @Test
    void sourceReferencesRejectNegativeRelativeCoordinates() {
        assertThrows(IllegalArgumentException.class, () -> new SourceReference(
                "snapshot:test", Optional.of(new BlockPosition(-1, 0, 0)), Optional.empty(), Optional.empty()));
    }

    @Test
    void generatedSceneRejectsInconsistentHierarchyAndUnknownMaterial() {
        var root = new SceneNode("node:root", "Root", Optional.empty(), Transform.IDENTITY,
                List.of(), List.of("node:missing"), Map.of(), List.of());
        assertThrows(IllegalArgumentException.class, () -> new GeneratedScene(
                ApiVersions.GENERATED_SCENE, "scene:bad", Transform.IDENTITY,
                List.of("node:root"), List.of(root), List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(), Map.of(), List.of(),
                new SceneStatistics(0, 0, 0, 0, 0, 0, 0)));

        var mesh = new MeshGroup(
                "mesh:test", "Test", List.of(triangle(Optional.of("material:missing"))), Map.of(), List.of());
        var meshNode = new SceneNode("node:mesh", "Mesh", Optional.empty(), Transform.IDENTITY,
                List.of("mesh:test"), List.of(), Map.of(), List.of());
        assertThrows(IllegalArgumentException.class, () -> new GeneratedScene(
                ApiVersions.GENERATED_SCENE, "scene:bad-material", Transform.IDENTITY,
                List.of("node:mesh"), List.of(meshNode), List.of(mesh), List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(), Map.of(), List.of(),
                new SceneStatistics(0, 0, 0, 3, 1, 1, 0)));
    }
}
