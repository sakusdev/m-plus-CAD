/*
 * SPDX-License-Identifier: MPL-2.0
 */
package dev.sakus.mcad.minecraft.runtime;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.sakus.mcad.api.ApiVersions;
import dev.sakus.mcad.api.BlockEntry;
import dev.sakus.mcad.api.BlockPosition;
import dev.sakus.mcad.api.CancellationToken;
import dev.sakus.mcad.api.CanonicalIdentifier;
import dev.sakus.mcad.api.ExportStatus;
import dev.sakus.mcad.api.IntSize3;
import dev.sakus.mcad.api.ProgressReporter;
import dev.sakus.mcad.api.ProjectSettings;
import dev.sakus.mcad.api.StructureSnapshot;
import dev.sakus.mcad.export.obj.ObjExporter;
import dev.sakus.mcad.gltf.GltfExporter;
import dev.sakus.mcad.markers.MarkerRuleSet;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MvpPipelineEndToEndTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void snapshotToGlbAndObjIsDeterministicAndKeepsBlockMeshesSeparate() throws IOException {
        StructureSnapshot snapshot = new StructureSnapshot(
                ApiVersions.STRUCTURE_SNAPSHOT,
                "snapshot/e2e",
                new IntSize3(2, 1, 1),
                Optional.of(new BlockPosition(-8, 64, 12)),
                List.of(
                        new BlockEntry(
                                new BlockPosition(0, 0, 0),
                                CanonicalIdentifier.parse("minecraft:stone"),
                                Map.of(),
                                Map.of()),
                        new BlockEntry(
                                new BlockPosition(1, 0, 0),
                                CanonicalIdentifier.parse("minecraft:dirt"),
                                Map.of(),
                                Map.of())),
                Map.of());
        ProjectSettings settings = RuntimeDefaults.projectSettings();
        MarkerRuleSet rules = new MarkerRuleSet(MarkerRuleSet.CURRENT_SCHEMA_VERSION, List.of());
        MvpPipeline pipeline = new MvpPipeline();

        Path firstGlb = temporaryDirectory.resolve("first/model.glb");
        Path secondGlb = temporaryDirectory.resolve("second/model.glb");
        Files.createDirectories(firstGlb.getParent());
        Files.createDirectories(secondGlb.getParent());

        MvpPipeline.Output first = pipeline.run(
                snapshot, settings, rules, new GltfExporter(), firstGlb,
                ProgressReporter.NONE, CancellationToken.NONE);
        MvpPipeline.Output second = pipeline.run(
                snapshot, settings, rules, new GltfExporter(), secondGlb,
                ProgressReporter.NONE, CancellationToken.NONE);

        assertEquals(ExportStatus.SUCCESS, first.exportResult().status());
        assertEquals(2, first.scene().meshes().size());
        assertEquals(2, first.scene().materials().size());
        assertArrayEquals(Files.readAllBytes(firstGlb), Files.readAllBytes(secondGlb));

        Path obj = temporaryDirectory.resolve("obj/model.obj");
        Files.createDirectories(obj.getParent());
        MvpPipeline.Output objOutput = pipeline.run(
                snapshot, settings, rules, new ObjExporter(), obj,
                ProgressReporter.NONE, CancellationToken.NONE);

        assertEquals(ExportStatus.SUCCESS, objOutput.exportResult().status());
        assertTrue(Files.isRegularFile(obj));
        assertTrue(Files.isRegularFile(obj.resolveSibling("model.mtl")));
    }
}
