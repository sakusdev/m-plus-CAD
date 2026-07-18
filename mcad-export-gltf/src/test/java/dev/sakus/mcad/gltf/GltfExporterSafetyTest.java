/*
 * SPDX-License-Identifier: MPL-2.0
 */
package dev.sakus.mcad.gltf;

import static dev.sakus.mcad.gltf.GltfTestFixtures.bone;
import static dev.sakus.mcad.gltf.GltfTestFixtures.curve;
import static dev.sakus.mcad.gltf.GltfTestFixtures.glbJson;
import static dev.sakus.mcad.gltf.GltfTestFixtures.material;
import static dev.sakus.mcad.gltf.GltfTestFixtures.mesh;
import static dev.sakus.mcad.gltf.GltfTestFixtures.scene;
import static dev.sakus.mcad.gltf.GltfTestFixtures.sceneWithNodes;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.sakus.mcad.api.CancellationToken;
import dev.sakus.mcad.api.CanonicalIdentifier;
import dev.sakus.mcad.api.DiagnosticSeverity;
import dev.sakus.mcad.api.ExportOptions;
import dev.sakus.mcad.api.ExportResult;
import dev.sakus.mcad.api.ExportStatus;
import dev.sakus.mcad.api.GeneratedScene;
import dev.sakus.mcad.api.MeshGroup;
import dev.sakus.mcad.api.MetadataValue;
import dev.sakus.mcad.api.ProgressReporter;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class GltfExporterSafetyTest {
    private final GltfExporter exporter = new GltfExporter();

    @TempDir
    Path temporaryDirectory;

    @Test
    void unsupportedSceneElementsAreDiagnosedAndPreservedInExtras() throws Exception {
        GeneratedScene scene = scene(
                List.of(mesh("mesh/triangle", Optional.empty(), false)),
                List.of(), List.of(curve()), List.of(bone()));

        var warningPreflight = exporter.preflight(scene, ExportOptions.EMPTY);
        assertTrue(warningPreflight.canExport());
        assertTrue(warningPreflight.diagnostics().stream().anyMatch(diagnostic ->
                diagnostic.severity() == DiagnosticSeverity.WARNING
                        && diagnostic.code().toString().equals("mcad:gltf/unsupported-scene-element")));

        ExportOptions strict = new ExportOptions(Map.of(
                new CanonicalIdentifier("mcad", "gltf/loss-policy"),
                new MetadataValue.StringValue("error")));
        assertFalse(exporter.preflight(scene, strict).canExport());

        Path destination = temporaryDirectory.resolve("unsupported.glb");
        ExportResult result = exporter.export(
                scene, destination, ExportOptions.EMPTY, ProgressReporter.NONE, CancellationToken.NONE);
        assertEquals(ExportStatus.SUCCESS, result.status());
        String json = glbJson(Files.readAllBytes(destination));
        assertTrue(json.contains("\"mcadCurves\":"));
        assertTrue(json.contains("\"mcadBones\":"));
        assertTrue(json.contains("curve/path"));
        assertTrue(json.contains("bone/root"));
    }

    @Test
    void serializationIsDeterministic() throws Exception {
        GeneratedScene scene = scene(
                List.of(mesh("mesh/triangle", Optional.of("material/test"), true)),
                List.of(material()), List.of());
        Path first = temporaryDirectory.resolve("first.glb");
        Path second = temporaryDirectory.resolve("second.glb");

        assertEquals(ExportStatus.SUCCESS, exporter.export(
                scene, first, ExportOptions.EMPTY, ProgressReporter.NONE, CancellationToken.NONE).status());
        assertEquals(ExportStatus.SUCCESS, exporter.export(
                scene, second, ExportOptions.EMPTY, ProgressReporter.NONE, CancellationToken.NONE).status());
        assertArrayEquals(Files.readAllBytes(first), Files.readAllBytes(second));
    }

    @Test
    void cancellationLeavesNoFinalOrTemporaryFiles() throws Exception {
        GeneratedScene scene = scene(List.of(mesh("mesh/triangle", Optional.empty(), false)), List.of(), List.of());
        Path destination = temporaryDirectory.resolve("cancelled.glb");

        ExportResult result = exporter.export(
                scene, destination, ExportOptions.EMPTY, ProgressReporter.NONE, () -> true);
        assertEquals(ExportStatus.CANCELLED, result.status());
        assertTrue(result.producedFiles().isEmpty());
        assertFalse(Files.exists(destination));
        try (var files = Files.list(temporaryDirectory)) {
            assertTrue(files.noneMatch(path -> path.getFileName().toString().startsWith(".mcad-gltf-")));
        }
    }

    @Test
    void completionProgressFailureDoesNotInvalidateCommittedOutput() throws Exception {
        GeneratedScene scene = scene(List.of(mesh("mesh/triangle", Optional.empty(), false)), List.of(), List.of());
        Path destination = temporaryDirectory.resolve("committed.glb");
        AtomicInteger reports = new AtomicInteger();

        ExportResult result = exporter.export(scene, destination, ExportOptions.EMPTY, update -> {
            reports.incrementAndGet();
            if (update.completed() == 4) {
                throw new IllegalStateException("consumer closed");
            }
        }, CancellationToken.NONE);

        assertEquals(ExportStatus.SUCCESS, result.status());
        assertTrue(Files.exists(destination));
        assertEquals(5, reports.get());
        assertTrue(result.diagnostics().stream().anyMatch(diagnostic ->
                diagnostic.code().toString().equals("mcad:gltf/progress-callback-failure")));
    }

    @Test
    void rejectsNonPortableDestinationNameBeforeCommit() throws Exception {
        GeneratedScene scene = scene(List.of(mesh("mesh/triangle", Optional.empty(), false)), List.of(), List.of());
        Path destination = temporaryDirectory.resolve("bad:name.glb");

        ExportResult result = exporter.export(
                scene, destination, ExportOptions.EMPTY, ProgressReporter.NONE, CancellationToken.NONE);
        assertEquals(ExportStatus.FAILED, result.status());
        assertFalse(Files.exists(destination));
        assertTrue(result.diagnostics().stream().anyMatch(diagnostic ->
                diagnostic.code().toString().equals("mcad:gltf/invalid-destination")));
    }

    @Test
    void unattachedMeshesArePreservedAsSyntheticRoots() throws Exception {
        MeshGroup mesh = mesh("mesh/unattached", Optional.empty(), false);
        GeneratedScene scene = sceneWithNodes(List.of(mesh), List.of(), List.of(), List.of());
        Path destination = temporaryDirectory.resolve("unattached.glb");

        ExportResult result = exporter.export(
                scene, destination, ExportOptions.EMPTY, ProgressReporter.NONE, CancellationToken.NONE);
        assertEquals(ExportStatus.SUCCESS, result.status());
        String json = glbJson(Files.readAllBytes(destination));
        assertTrue(json.contains("synthetic/unattached/mesh/unattached"));
        assertTrue(result.diagnostics().stream().anyMatch(diagnostic ->
                diagnostic.code().toString().equals("mcad:gltf/unattached-mesh-preserved")));
    }
}
