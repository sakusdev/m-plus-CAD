/*
 * SPDX-License-Identifier: MPL-2.0
 */
package dev.sakus.mcad.livelink;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.sakus.mcad.api.ApiVersions;
import dev.sakus.mcad.api.GeneratedScene;
import dev.sakus.mcad.api.MeshGroup;
import dev.sakus.mcad.api.MeshPrimitive;
import dev.sakus.mcad.api.SceneNode;
import dev.sakus.mcad.api.SceneStatistics;
import dev.sakus.mcad.api.Transform;
import dev.sakus.mcad.api.Vec3d;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class SceneDeltaEncoderTest {
    private final SceneDeltaEncoder encoder = new SceneDeltaEncoder();

    @Test
    void fullThenUnchangedThenChangedMeshAreDeterministic() {
        LiveSceneSnapshot first = LiveSceneSnapshot.capture(scene(0.0));
        SceneDeltaEncoder.EncodedUpdate full = encoder.encode(null, first, 1L, false);
        assertTrue(full.changed());
        assertTrue(full.full());
        assertEquals(2, full.upsertCount());
        assertTrue(full.json().contains("\"protocol\":\"mcad-live-link\""));
        assertTrue(full.json().contains("\"revision\":1"));

        SceneDeltaEncoder.EncodedUpdate unchanged = encoder.encode(first, first, 2L, false);
        assertFalse(unchanged.changed());
        assertFalse(unchanged.full());
        assertEquals(0, unchanged.upsertCount());
        assertEquals(0, unchanged.removeCount());

        LiveSceneSnapshot changed = LiveSceneSnapshot.capture(scene(2.0));
        SceneDeltaEncoder.EncodedUpdate delta = encoder.encode(first, changed, 3L, false);
        assertTrue(delta.changed());
        assertFalse(delta.full());
        assertEquals(1, delta.upsertCount());
        assertEquals(0, delta.removeCount());
        assertTrue(delta.json().contains("\"meshes\":[{"));
        assertTrue(delta.json().contains("[2.0,0.0,0.0]"));
    }

    @Test
    void removedStableIdsAreEmittedInSortedBuckets() {
        LiveSceneSnapshot previous = LiveSceneSnapshot.capture(scene(0.0));
        LiveSceneSnapshot empty = LiveSceneSnapshot.capture(emptyScene());
        SceneDeltaEncoder.EncodedUpdate delta = encoder.encode(previous, empty, 4L, false);
        assertTrue(delta.changed());
        assertEquals(0, delta.upsertCount());
        assertEquals(2, delta.removeCount());
        assertTrue(delta.json().contains("\"meshes\":[\"mesh/test\"]"));
        assertTrue(delta.json().contains("\"nodes\":[\"node/root\"]"));
    }

    private static GeneratedScene scene(double x) {
        MeshPrimitive primitive = new MeshPrimitive(
                List.of(new Vec3d(x, 0, 0), new Vec3d(1, 0, 0), new Vec3d(0, 1, 0)),
                List.of(new Vec3d(0, 0, 1), new Vec3d(0, 0, 1), new Vec3d(0, 0, 1)),
                List.of(0, 1, 2),
                Optional.empty(),
                List.of(),
                Map.of());
        MeshGroup mesh = new MeshGroup("mesh/test", "Test", List.of(primitive), Map.of(), List.of());
        SceneNode root = new SceneNode(
                "node/root",
                "Root",
                Optional.empty(),
                Transform.IDENTITY,
                List.of(mesh.stableId()),
                List.of(),
                Map.of(),
                List.of());
        return new GeneratedScene(
                ApiVersions.GENERATED_SCENE,
                "scene/live-link-test",
                Transform.IDENTITY,
                List.of(root.stableId()),
                List.of(root),
                List.of(mesh),
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                Map.of(), List.of(),
                new SceneStatistics(1, 1, 0, 3, 1, 1, 0));
    }

    private static GeneratedScene emptyScene() {
        return new GeneratedScene(
                ApiVersions.GENERATED_SCENE,
                "scene/live-link-test",
                Transform.IDENTITY,
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                Map.of(), List.of(),
                new SceneStatistics(0, 0, 0, 0, 0, 0, 0));
    }
}
