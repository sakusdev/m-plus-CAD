/*
 * SPDX-License-Identifier: MPL-2.0
 */
package dev.sakus.mcad.export.obj;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.sakus.mcad.api.AlphaMode;
import dev.sakus.mcad.api.ApiVersions;
import dev.sakus.mcad.api.CancellationToken;
import dev.sakus.mcad.api.CanonicalIdentifier;
import dev.sakus.mcad.api.Color3d;
import dev.sakus.mcad.api.Color4d;
import dev.sakus.mcad.api.ExportOptions;
import dev.sakus.mcad.api.ExportStatus;
import dev.sakus.mcad.api.GeneratedScene;
import dev.sakus.mcad.api.MaterialDefinition;
import dev.sakus.mcad.api.MeshGroup;
import dev.sakus.mcad.api.MeshPrimitive;
import dev.sakus.mcad.api.MetadataValue;
import dev.sakus.mcad.api.ProgressReporter;
import dev.sakus.mcad.api.SceneNode;
import dev.sakus.mcad.api.SceneStatistics;
import dev.sakus.mcad.api.Transform;
import dev.sakus.mcad.api.Vec3d;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ObjExporterTest {
    @TempDir
    Path temporaryDirectory;

    private final ObjExporter exporter = new ObjExporter();

    @Test
    void singleCubeMatchesGoldenFilesAndPublishesBothOutputs() throws IOException {
        GeneratedScene scene = scene(List.of(group("mesh/stone", "Stone", cubePrimitive("material/stone"))),
                List.of(defaultMaterial("material/stone", "Stone")));
        Path destination = temporaryDirectory.resolve("cube.obj");

        var result = exporter.export(
                scene, destination, ExportOptions.EMPTY, ProgressReporter.NONE, CancellationToken.NONE);

        assertEquals(ExportStatus.SUCCESS, result.status());
        assertEquals(2, result.producedFiles().size());
        assertArrayEquals(fixture("single-cube.obj"), Files.readAllBytes(destination));
        assertArrayEquals(fixture("single-cube.mtl"), Files.readAllBytes(temporaryDirectory.resolve("cube.mtl")));
    }

    @Test
    void keepsTwoMeshGroupsSeparateAndAssignsMaterials() throws IOException {
        GeneratedScene scene = scene(
                List.of(
                        group("mesh/example/a", "Block A", trianglePrimitive("material/a")),
                        group("mesh/example/b", "Block B", translatedTrianglePrimitive("material/b"))),
                List.of(
                        defaultMaterial("material/a", "Material A"),
                        defaultMaterial("material/b", "Material B")));
        Path destination = temporaryDirectory.resolve("groups.obj");

        var result = exporter.export(
                scene, destination, ExportOptions.EMPTY, ProgressReporter.NONE, CancellationToken.NONE);
        String obj = Files.readString(destination, StandardCharsets.UTF_8);
        String mtl = Files.readString(temporaryDirectory.resolve("groups.mtl"), StandardCharsets.UTF_8);

        assertEquals(ExportStatus.SUCCESS, result.status());
        assertTrue(obj.contains("o Block_A\ng Block_A\nusemtl Material_A"));
        assertTrue(obj.contains("o Block_B\ng Block_B\nusemtl Material_B"));
        assertTrue(mtl.contains("newmtl Material_A"));
        assertTrue(mtl.contains("newmtl Material_B"));
    }

    @Test
    void sanitizesNamesAndResolvesCollisionsDeterministically() throws IOException {
        GeneratedScene scene = scene(
                List.of(
                        group("mesh/example/a", "Stone / Block", trianglePrimitive(null)),
                        group("mesh/example/b", "Stone ? Block", translatedTrianglePrimitive(null))),
                List.of());
        Path destination = temporaryDirectory.resolve("names.obj");

        var result = exporter.export(
                scene, destination, ExportOptions.EMPTY, ProgressReporter.NONE, CancellationToken.NONE);
        String obj = Files.readString(destination, StandardCharsets.UTF_8);

        assertEquals(ExportStatus.SUCCESS, result.status());
        assertTrue(obj.contains("o Stone_Block\n"));
        assertTrue(obj.contains("o Stone_Block_2\n"));
        assertTrue(result.diagnostics().stream().anyMatch(diagnostic ->
                diagnostic.code().equals(CanonicalIdentifier.parse("mcad:obj/name_substituted"))));
    }

    @Test
    void appliesOriginScaleAxisAndReversesLeftHandedWinding() throws IOException {
        GeneratedScene scene = scene(
                List.of(group("mesh/transform", "Transform", new MeshPrimitive(
                        List.of(new Vec3d(1, 2, 3), new Vec3d(2, 2, 3), new Vec3d(1, 3, 3)),
                        List.of(new Vec3d(0, 0, 1), new Vec3d(0, 0, 1), new Vec3d(0, 0, 1)),
                        List.of(0, 1, 2), Optional.empty(), List.of(), Map.of()))),
                List.of());
        ExportOptions rightHandedZUp = options(
                List.of(1.0, 1.0, 1.0), List.of(0.0, 0.0, 0.0), 2.0, "right_handed_z_up");
        Path destination = temporaryDirectory.resolve("transform.obj");

        assertEquals(ExportStatus.SUCCESS, exporter.export(
                scene, destination, rightHandedZUp, ProgressReporter.NONE, CancellationToken.NONE).status());
        String transformed = Files.readString(destination, StandardCharsets.UTF_8);
        assertTrue(transformed.contains("v 0 -4 2\n"));

        Path leftDestination = temporaryDirectory.resolve("left.obj");
        ExportOptions leftHanded = options(
                List.of(0.0, 0.0, 0.0), List.of(0.0, 0.0, 0.0), 1.0, "left_handed_y_up");
        assertEquals(ExportStatus.SUCCESS, exporter.export(
                scene, leftDestination, leftHanded, ProgressReporter.NONE, CancellationToken.NONE).status());
        String left = Files.readString(leftDestination, StandardCharsets.UTF_8);
        assertTrue(left.contains("f 1//1 3//3 2//2\n"));
    }

    @Test
    void cancellationAndDestinationFailureLeaveNoPublishedOrTemporaryFiles() throws IOException {
        GeneratedScene scene = scene(List.of(group("mesh/cancel", "Cancel", cubePrimitive(null))), List.of());
        Path cancelledDirectory = temporaryDirectory.resolve("cancelled");
        Files.createDirectory(cancelledDirectory);

        AtomicInteger cancellationChecks = new AtomicInteger();
        var cancelled = exporter.export(
                scene,
                cancelledDirectory.resolve("model.obj"),
                ExportOptions.EMPTY,
                ProgressReporter.NONE,
                () -> cancellationChecks.incrementAndGet() >= 2);
        assertEquals(ExportStatus.CANCELLED, cancelled.status());
        assertTrue(cancelled.producedFiles().isEmpty());
        try (var entries = Files.list(cancelledDirectory)) {
            assertEquals(0, entries.count());
        }

        Path missingParent = temporaryDirectory.resolve("missing").resolve("model.obj");
        var failed = exporter.export(
                scene, missingParent, ExportOptions.EMPTY, ProgressReporter.NONE, CancellationToken.NONE);
        assertEquals(ExportStatus.FAILED, failed.status());
        assertTrue(failed.producedFiles().isEmpty());
        assertFalse(Files.exists(missingParent));
    }

    @Test
    void outputBytesAreDeterministic() throws IOException {
        GeneratedScene scene = scene(
                List.of(
                        group("mesh/b", "Same", translatedTrianglePrimitive("material/b")),
                        group("mesh/a", "Same", trianglePrimitive("material/a"))),
                List.of(
                        defaultMaterial("material/b", "Shared"),
                        defaultMaterial("material/a", "Shared")));
        Path firstDirectory = temporaryDirectory.resolve("first");
        Path secondDirectory = temporaryDirectory.resolve("second");
        Files.createDirectories(firstDirectory);
        Files.createDirectories(secondDirectory);

        assertEquals(ExportStatus.SUCCESS, exporter.export(
                scene, firstDirectory.resolve("model.obj"), ExportOptions.EMPTY,
                ProgressReporter.NONE, CancellationToken.NONE).status());
        assertEquals(ExportStatus.SUCCESS, exporter.export(
                scene, secondDirectory.resolve("model.obj"), ExportOptions.EMPTY,
                ProgressReporter.NONE, CancellationToken.NONE).status());

        assertArrayEquals(
                Files.readAllBytes(firstDirectory.resolve("model.obj")),
                Files.readAllBytes(secondDirectory.resolve("model.obj")));
        assertArrayEquals(
                Files.readAllBytes(firstDirectory.resolve("model.mtl")),
                Files.readAllBytes(secondDirectory.resolve("model.mtl")));
    }

    @Test
    void preflightReportsUnsupportedDataAndHonoursFailPolicy() {
        MeshPrimitive vertexColoured = new MeshPrimitive(
                List.of(new Vec3d(0, 0, 0), new Vec3d(1, 0, 0), new Vec3d(0, 1, 0)),
                List.of(new Vec3d(0, 0, 1), new Vec3d(0, 0, 1), new Vec3d(0, 0, 1)),
                List.of(0, 1, 2), Optional.empty(),
                List.of(new Color4d(1, 0, 0, 1), new Color4d(0, 1, 0, 1), new Color4d(0, 0, 1, 1)),
                Map.of());
        GeneratedScene scene = scene(List.of(group("mesh/colour", "Colour", vertexColoured)), List.of());
        ExportOptions fail = new ExportOptions(Map.of(
                CanonicalIdentifier.parse("mcad:loss_policy"), new MetadataValue.StringValue("fail")));

        assertTrue(exporter.preflight(scene, ExportOptions.EMPTY).canExport());
        assertFalse(exporter.preflight(scene, fail).canExport());
    }

    private static GeneratedScene scene(List<MeshGroup> groups, List<MaterialDefinition> materials) {
        List<String> meshIds = groups.stream().map(MeshGroup::stableId).toList();
        SceneNode root = new SceneNode(
                "node/root", "Root", Optional.empty(), Transform.IDENTITY,
                meshIds, List.of(), Map.of(), List.of());
        long vertices = groups.stream().flatMap(group -> group.primitives().stream())
                .mapToLong(primitive -> primitive.positions().size()).sum();
        long triangles = groups.stream().flatMap(group -> group.primitives().stream())
                .mapToLong(primitive -> primitive.indices().size() / 3L).sum();
        return new GeneratedScene(
                ApiVersions.GENERATED_SCENE,
                "scene/test",
                List.of(root.stableId()),
                List.of(root),
                groups,
                materials,
                List.of(), List.of(), List.of(), List.of(), List.of(),
                Map.of(), List.of(),
                new SceneStatistics(0, 0, 0, vertices, triangles, groups.size(), materials.size()));
    }

    private static MeshGroup group(String id, String name, MeshPrimitive primitive) {
        return new MeshGroup(id, name, List.of(primitive), Map.of());
    }

    private static MaterialDefinition defaultMaterial(String id, String name) {
        return new MaterialDefinition(
                id, name,
                new Color4d(0.5, 0.25, 0.125, 1.0),
                0.0, 1.0,
                new Color3d(0.0, 0.0, 0.0), 0.0,
                AlphaMode.OPAQUE, OptionalDouble.empty(), List.of(), Map.of());
    }

    private static MeshPrimitive trianglePrimitive(String materialId) {
        return new MeshPrimitive(
                List.of(new Vec3d(0, 0, 0), new Vec3d(1, 0, 0), new Vec3d(0, 1, 0)),
                List.of(new Vec3d(0, 0, 1), new Vec3d(0, 0, 1), new Vec3d(0, 0, 1)),
                List.of(0, 1, 2), Optional.ofNullable(materialId), List.of(), Map.of());
    }

    private static MeshPrimitive translatedTrianglePrimitive(String materialId) {
        return new MeshPrimitive(
                List.of(new Vec3d(2, 0, 0), new Vec3d(3, 0, 0), new Vec3d(2, 1, 0)),
                List.of(new Vec3d(0, 0, 1), new Vec3d(0, 0, 1), new Vec3d(0, 0, 1)),
                List.of(0, 1, 2), Optional.ofNullable(materialId), List.of(), Map.of());
    }

    private static MeshPrimitive cubePrimitive(String materialId) {
        List<Vec3d> positions = new ArrayList<>();
        List<Vec3d> normals = new ArrayList<>();
        List<Integer> indices = new ArrayList<>();
        addFace(positions, normals, indices, new Vec3d(0, 0, -1),
                new Vec3d(0, 0, 0), new Vec3d(0, 1, 0), new Vec3d(1, 1, 0), new Vec3d(1, 0, 0));
        addFace(positions, normals, indices, new Vec3d(0, 0, 1),
                new Vec3d(0, 0, 1), new Vec3d(1, 0, 1), new Vec3d(1, 1, 1), new Vec3d(0, 1, 1));
        addFace(positions, normals, indices, new Vec3d(-1, 0, 0),
                new Vec3d(0, 0, 0), new Vec3d(0, 0, 1), new Vec3d(0, 1, 1), new Vec3d(0, 1, 0));
        addFace(positions, normals, indices, new Vec3d(1, 0, 0),
                new Vec3d(1, 0, 0), new Vec3d(1, 1, 0), new Vec3d(1, 1, 1), new Vec3d(1, 0, 1));
        addFace(positions, normals, indices, new Vec3d(0, -1, 0),
                new Vec3d(0, 0, 0), new Vec3d(1, 0, 0), new Vec3d(1, 0, 1), new Vec3d(0, 0, 1));
        addFace(positions, normals, indices, new Vec3d(0, 1, 0),
                new Vec3d(0, 1, 0), new Vec3d(0, 1, 1), new Vec3d(1, 1, 1), new Vec3d(1, 1, 0));
        return new MeshPrimitive(positions, normals, indices, Optional.ofNullable(materialId), List.of(), Map.of());
    }

    private static void addFace(
            List<Vec3d> positions,
            List<Vec3d> normals,
            List<Integer> indices,
            Vec3d normal,
            Vec3d first,
            Vec3d second,
            Vec3d third,
            Vec3d fourth) {
        int base = positions.size();
        positions.addAll(List.of(first, second, third, fourth));
        normals.addAll(List.of(normal, normal, normal, normal));
        indices.addAll(List.of(base, base + 1, base + 2, base, base + 2, base + 3));
    }

    private static ExportOptions options(
            List<Double> origin,
            List<Double> rotation,
            double scale,
            String axis) {
        return new ExportOptions(Map.of(
                CanonicalIdentifier.parse("mcad:transform/origin_offset"), vector(origin),
                CanonicalIdentifier.parse("mcad:transform/rotation_degrees"), vector(rotation),
                CanonicalIdentifier.parse("mcad:transform/unit_scale"), new MetadataValue.DoubleValue(scale),
                CanonicalIdentifier.parse("mcad:transform/target_axis"), new MetadataValue.StringValue(axis)));
    }

    private static MetadataValue.ListValue vector(List<Double> values) {
        return new MetadataValue.ListValue(values.stream()
                .map(value -> (MetadataValue) new MetadataValue.DoubleValue(value))
                .toList());
    }

    private static byte[] fixture(String fileName) throws IOException {
        Path projectRelative = Path.of("..", "fixtures", "obj", fileName).normalize();
        Path rootRelative = Path.of("fixtures", "obj", fileName).normalize();
        return Files.readAllBytes(Files.exists(projectRelative) ? projectRelative : rootRelative);
    }
}
