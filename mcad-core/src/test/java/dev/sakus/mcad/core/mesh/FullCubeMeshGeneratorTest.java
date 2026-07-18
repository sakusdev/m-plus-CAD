/*
 * SPDX-License-Identifier: MPL-2.0
 */
package dev.sakus.mcad.core.mesh;

import dev.sakus.mcad.api.ApiVersions;
import dev.sakus.mcad.api.BlockEntry;
import dev.sakus.mcad.api.BlockPosition;
import dev.sakus.mcad.api.CancellationToken;
import dev.sakus.mcad.api.CanonicalIdentifier;
import dev.sakus.mcad.api.CollisionKind;
import dev.sakus.mcad.api.GeneratedScene;
import dev.sakus.mcad.api.IntSize3;
import dev.sakus.mcad.api.MeshGroup;
import dev.sakus.mcad.api.MeshPrimitive;
import dev.sakus.mcad.api.ProjectSettings;
import dev.sakus.mcad.api.StructureSnapshot;
import dev.sakus.mcad.api.Vec3d;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CancellationException;

/** Self-contained test runner so the required cases remain executable before mcad-core gets JUnit wiring. */
public final class FullCubeMeshGeneratorTest {
    private static final CanonicalIdentifier STONE = new CanonicalIdentifier("example", "stone");
    private static final CanonicalIdentifier GLASS = new CanonicalIdentifier("example", "glass");
    private final FullCubeMeshGenerator generator = new FullCubeMeshGenerator();

    public static void main(String[] args) throws Exception {
        FullCubeMeshGeneratorTest test = new FullCubeMeshGeneratorTest();
        test.oneCubeHasOutwardWindingAndNormals();
        test.twoAdjacentSameBlocksRemoveBothSharedFaces();
        test.twoAdjacentDifferentBlocksRemainSeparateAndCullBoundary();
        test.solidTwoByTwoByTwoHasOnlyExteriorFaces();
        test.hollowThreeByThreeByThreeKeepsCavityFaces();
        test.disconnectedIslandsShareStableBlockGroup();
        test.outputIsDeterministicAndMatchesFixture();
        test.cancellationStopsWithoutReturningPartialScene();
        test.hiddenFaceRemovalCanBeDisabled();
        test.negativeWorldOriginDoesNotAffectRelativeGeometry();
        test.configuredBlockLimitIsEnforced();
    }

    void oneCubeHasOutwardWindingAndNormals() {
        GeneratedScene scene = generate(snapshot("one", 1, 1, 1, block(0, 0, 0, STONE)), true);
        assertStatistics(scene, 1, 6, 0, 24, 12, 1);
        MeshPrimitive primitive = onlyPrimitive(scene);
        for (int triangle = 0; triangle < primitive.indices().size(); triangle += 3) {
            Vec3d a = primitive.positions().get(primitive.indices().get(triangle));
            Vec3d b = primitive.positions().get(primitive.indices().get(triangle + 1));
            Vec3d c = primitive.positions().get(primitive.indices().get(triangle + 2));
            Vec3d expected = primitive.normals().get(primitive.indices().get(triangle));
            Vec3d actual = cross(subtract(b, a), subtract(c, a));
            assertTrue(dot(actual, expected) > 0.0, "triangle winding must face outward");
        }
    }

    void twoAdjacentSameBlocksRemoveBothSharedFaces() {
        GeneratedScene scene = generate(snapshot("same", 2, 1, 1,
                block(0, 0, 0, STONE), block(1, 0, 0, STONE)), true);
        assertStatistics(scene, 2, 10, 2, 40, 20, 1);
    }

    void twoAdjacentDifferentBlocksRemainSeparateAndCullBoundary() {
        GeneratedScene scene = generate(snapshot("different", 2, 1, 1,
                block(0, 0, 0, STONE), block(1, 0, 0, GLASS)), true);
        assertStatistics(scene, 2, 10, 2, 40, 20, 2);
        assertEquals(List.of("mesh/block/example/glass", "mesh/block/example/stone"),
                scene.meshes().stream().map(MeshGroup::stableId).toList(), "stable mesh ordering");
    }

    void solidTwoByTwoByTwoHasOnlyExteriorFaces() {
        List<BlockEntry> blocks = new ArrayList<>();
        for (int y = 0; y < 2; y++) {
            for (int z = 0; z < 2; z++) {
                for (int x = 0; x < 2; x++) {
                    blocks.add(block(x, y, z, STONE));
                }
            }
        }
        GeneratedScene scene = generate(snapshot("solid", 2, 2, 2, blocks), true);
        assertStatistics(scene, 8, 24, 24, 96, 48, 1);
    }

    void hollowThreeByThreeByThreeKeepsCavityFaces() {
        List<BlockEntry> blocks = new ArrayList<>();
        for (int y = 0; y < 3; y++) {
            for (int z = 0; z < 3; z++) {
                for (int x = 0; x < 3; x++) {
                    if (x != 1 || y != 1 || z != 1) {
                        blocks.add(block(x, y, z, STONE));
                    }
                }
            }
        }
        GeneratedScene scene = generate(snapshot("hollow", 3, 3, 3, blocks), true);
        assertStatistics(scene, 26, 60, 96, 240, 120, 1);
    }

    void disconnectedIslandsShareStableBlockGroup() {
        GeneratedScene scene = generate(snapshot("islands", 3, 1, 1,
                block(0, 0, 0, STONE), block(2, 0, 0, STONE)), true);
        assertStatistics(scene, 2, 12, 0, 48, 24, 1);
    }

    void outputIsDeterministicAndMatchesFixture() throws IOException {
        List<BlockEntry> blocks = new ArrayList<>(List.of(
                block(0, 0, 0, new CanonicalIdentifier("example", "a")),
                block(1, 0, 0, new CanonicalIdentifier("example", "a")),
                block(0, 1, 0, new CanonicalIdentifier("example", "b"))));
        StructureSnapshot ordered = snapshot("deterministic", 2, 2, 1, blocks);
        Collections.reverse(blocks);
        StructureSnapshot reversed = snapshot("deterministic", 2, 2, 1, blocks);

        GeneratedScene first = generate(ordered, true);
        GeneratedScene second = generate(reversed, true);
        assertEquals(first, second, "logical scene must not depend on input collection order");

        Path fixture = Path.of("fixtures/mesh/full-cube-deterministic.txt");
        assertEquals(Files.readString(fixture).strip(), signature(first), "fixture signature");
    }

    void cancellationStopsWithoutReturningPartialScene() {
        StructureSnapshot snapshot = snapshot("cancel", 1, 1, 1, block(0, 0, 0, STONE));
        assertThrows(CancellationException.class, () -> generator.generate(
                snapshot, settings(true, 100), () -> true, update -> { }));
    }

    void hiddenFaceRemovalCanBeDisabled() {
        GeneratedScene scene = generate(snapshot("no-cull", 2, 1, 1,
                block(0, 0, 0, STONE), block(1, 0, 0, STONE)), false);
        assertStatistics(scene, 2, 12, 0, 48, 24, 1);
    }

    void negativeWorldOriginDoesNotAffectRelativeGeometry() {
        StructureSnapshot snapshot = new StructureSnapshot(
                ApiVersions.STRUCTURE_SNAPSHOT,
                "negative-origin",
                new IntSize3(1, 1, 1),
                Optional.of(new BlockPosition(-100, -20, -300)),
                List.of(block(0, 0, 0, STONE)),
                Map.of());
        GeneratedScene scene = generate(snapshot, true);
        assertEquals(new Vec3d(0.0, 0.0, 0.0), onlyPrimitive(scene).positions().get(0),
                "world origin must not be baked into relative vertices");
    }

    void configuredBlockLimitIsEnforced() {
        StructureSnapshot snapshot = snapshot("limit", 2, 1, 1,
                block(0, 0, 0, STONE), block(1, 0, 0, STONE));
        assertThrows(IllegalArgumentException.class, () -> generator.generate(
                snapshot, settings(true, 1), CancellationToken.NONE, update -> { }));
    }

    private GeneratedScene generate(StructureSnapshot snapshot, boolean hiddenFaceRemoval) {
        return generator.generate(snapshot, settings(hiddenFaceRemoval, 1_000_000),
                CancellationToken.NONE, update -> { });
    }

    private static ProjectSettings settings(boolean hiddenFaceRemoval, long maximumBlocks) {
        return new ProjectSettings(
                ApiVersions.PROJECT_SETTINGS,
                new ProjectSettings.SelectionSettings(maximumBlocks, false),
                new ProjectSettings.GeometrySettings(hiddenFaceRemoval, true),
                new ProjectSettings.MeshSeparationSettings(List.of(ProjectSettings.SeparationKey.BLOCK_IDENTIFIER)),
                new ProjectSettings.MaterialSettings(ProjectSettings.MaterialMode.NONE, Optional.empty()),
                new ProjectSettings.TransformSettings(
                        ProjectSettings.OriginMode.SELECTION_MINIMUM,
                        Vec3d.ZERO,
                        Vec3d.ZERO,
                        1.0,
                        ProjectSettings.AxisConvention.INTERNAL_RIGHT_HANDED_Y_UP),
                new ProjectSettings.MarkerSettings(false, true, false),
                new ProjectSettings.OptimizationSettings(false, false, true),
                new ProjectSettings.AnimationSettings(false, 20),
                new ProjectSettings.CollisionSettings(false, CollisionKind.SOLID),
                new ProjectSettings.OutputSettings(
                        new CanonicalIdentifier("mcad", "test"),
                        "build/test-output",
                        ProjectSettings.LossPolicy.FAIL,
                        Map.of()),
                new ProjectSettings.PreviewSettings(false, true, false),
                new ProjectSettings.AdvancedSettings(1, 64L * 1024L * 1024L));
    }

    private static StructureSnapshot snapshot(String id, int width, int height, int depth, BlockEntry... blocks) {
        return snapshot(id, width, height, depth, List.of(blocks));
    }

    private static StructureSnapshot snapshot(
            String id, int width, int height, int depth, List<BlockEntry> blocks) {
        return new StructureSnapshot(
                ApiVersions.STRUCTURE_SNAPSHOT,
                id,
                new IntSize3(width, height, depth),
                Optional.empty(),
                blocks,
                Map.of());
    }

    private static BlockEntry block(int x, int y, int z, CanonicalIdentifier id) {
        return new BlockEntry(new BlockPosition(x, y, z), id, Map.of(), Map.of());
    }

    private static MeshPrimitive onlyPrimitive(GeneratedScene scene) {
        assertEquals(1, scene.meshes().size(), "mesh count");
        assertEquals(1, scene.meshes().get(0).primitives().size(), "primitive count");
        return scene.meshes().get(0).primitives().get(0);
    }

    private static void assertStatistics(
            GeneratedScene scene,
            long blocks,
            long visibleFaces,
            long removedFaces,
            long vertices,
            long triangles,
            long meshes) {
        assertEquals(blocks, scene.statistics().sourceBlockCount(), "source blocks");
        assertEquals(visibleFaces, scene.statistics().visibleFaceCount(), "visible faces");
        assertEquals(removedFaces, scene.statistics().removedHiddenFaceCount(), "removed faces");
        assertEquals(vertices, scene.statistics().vertexCount(), "vertices");
        assertEquals(triangles, scene.statistics().triangleCount(), "triangles");
        assertEquals(meshes, scene.statistics().meshCount(), "meshes");
    }

    private static String signature(GeneratedScene scene) {
        StringBuilder result = new StringBuilder();
        result.append("scene=").append(scene.sceneId()).append('\n');
        result.append("meshes=").append(String.join(",", scene.meshes().stream().map(MeshGroup::stableId).toList()))
                .append('\n');
        var stats = scene.statistics();
        result.append("stats=")
                .append(stats.sourceBlockCount()).append(',')
                .append(stats.visibleFaceCount()).append(',')
                .append(stats.removedHiddenFaceCount()).append(',')
                .append(stats.vertexCount()).append(',')
                .append(stats.triangleCount()).append(',')
                .append(stats.meshCount()).append(',')
                .append(stats.materialCount()).append('\n');
        for (MeshGroup mesh : scene.meshes()) {
            MeshPrimitive primitive = mesh.primitives().get(0);
            result.append(mesh.stableId()).append('=')
                    .append(primitive.positions().size()).append(',')
                    .append(primitive.indices().size() / 3).append(',')
                    .append(primitive.indices().size() / 6).append('\n');
        }
        return result.toString().strip();
    }

    private static Vec3d subtract(Vec3d left, Vec3d right) {
        return new Vec3d(left.x() - right.x(), left.y() - right.y(), left.z() - right.z());
    }

    private static Vec3d cross(Vec3d left, Vec3d right) {
        return new Vec3d(
                left.y() * right.z() - left.z() * right.y(),
                left.z() * right.x() - left.x() * right.z(),
                left.x() * right.y() - left.y() * right.x());
    }

    private static double dot(Vec3d left, Vec3d right) {
        return left.x() * right.x() + left.y() * right.y() + left.z() * right.z();
    }

    private static void assertTrue(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static void assertEquals(Object expected, Object actual, String message) {
        if (!expected.equals(actual)) {
            throw new AssertionError(message + ": expected=" + expected + ", actual=" + actual);
        }
    }

    private static void assertThrows(Class<? extends Throwable> expected, ThrowingRunnable runnable) {
        try {
            runnable.run();
        } catch (Throwable throwable) {
            if (expected.isInstance(throwable)) {
                return;
            }
            throw new AssertionError("expected " + expected.getName() + " but got " + throwable, throwable);
        }
        throw new AssertionError("expected " + expected.getName() + " to be thrown");
    }

    @FunctionalInterface
    private interface ThrowingRunnable {
        void run() throws Exception;
    }
}
