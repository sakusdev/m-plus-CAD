/*
 * SPDX-License-Identifier: MPL-2.0
 */
package dev.sakus.mcad.export.obj;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.sakus.mcad.api.ApiVersions;
import dev.sakus.mcad.api.CancellationToken;
import dev.sakus.mcad.api.ExportOptions;
import dev.sakus.mcad.api.ExportStatus;
import dev.sakus.mcad.api.GeneratedScene;
import dev.sakus.mcad.api.MeshGroup;
import dev.sakus.mcad.api.MeshPrimitive;
import dev.sakus.mcad.api.ProgressReporter;
import dev.sakus.mcad.api.Quaterniond;
import dev.sakus.mcad.api.SceneNode;
import dev.sakus.mcad.api.SceneStatistics;
import dev.sakus.mcad.api.Transform;
import dev.sakus.mcad.api.Vec3d;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ObjSceneOriginTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void appliesSceneOriginBeforeExporterBoundaryTransform() throws IOException {
        MeshPrimitive primitive = new MeshPrimitive(
                List.of(new Vec3d(0, 0, 0), new Vec3d(1, 0, 0), new Vec3d(0, 1, 0)),
                List.of(new Vec3d(0, 0, 1), new Vec3d(0, 0, 1), new Vec3d(0, 0, 1)),
                List.of(0, 1, 2),
                Optional.empty(),
                List.of(),
                Map.of());
        MeshGroup mesh = new MeshGroup("mesh/test", "Test", List.of(primitive), Map.of(), List.of());
        SceneNode root = new SceneNode(
                "node/root", "Root", Optional.empty(), Transform.IDENTITY,
                List.of(mesh.stableId()), List.of(), Map.of(), List.of());
        GeneratedScene scene = new GeneratedScene(
                ApiVersions.GENERATED_SCENE,
                "scene/obj-origin",
                new Transform(new Vec3d(-1, -2, -3), Quaterniond.IDENTITY, Vec3d.ONE),
                List.of(root.stableId()),
                List.of(root),
                List.of(mesh),
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                Map.of(), List.of(),
                new SceneStatistics(1, 1, 0, 3, 1, 1, 0));

        Path destination = temporaryDirectory.resolve("origin.obj");
        var result = new ObjExporter().export(
                scene, destination, ExportOptions.EMPTY, ProgressReporter.NONE, CancellationToken.NONE);

        assertEquals(ExportStatus.SUCCESS, result.status());
        assertTrue(Files.readString(destination).contains("v -1 -2 -3\n"));
    }
}
