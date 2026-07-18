/*
 * SPDX-License-Identifier: MPL-2.0
 */
package dev.sakus.mcad.minecraft.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.sakus.mcad.api.ApiVersions;
import dev.sakus.mcad.api.BlockEntry;
import dev.sakus.mcad.api.BlockPosition;
import dev.sakus.mcad.api.CancellationToken;
import dev.sakus.mcad.api.CanonicalIdentifier;
import dev.sakus.mcad.api.Color3d;
import dev.sakus.mcad.api.IntSize3;
import dev.sakus.mcad.api.ProgressReporter;
import dev.sakus.mcad.api.ProjectSettings;
import dev.sakus.mcad.api.SourceReference;
import dev.sakus.mcad.api.StructureSnapshot;
import dev.sakus.mcad.api.Transform;
import dev.sakus.mcad.api.Vec3d;
import dev.sakus.mcad.core.mesh.FullCubeMeshGenerator;
import dev.sakus.mcad.markers.MarkerDirective;
import dev.sakus.mcad.markers.MarkerInterpretationResult;
import dev.sakus.mcad.materials.MaterialResolver;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import org.junit.jupiter.api.Test;

class SceneAssemblerTest {
    @Test
    void appliesConfiguredCentreOriginMaterialsMarkersAndDefaultCollision() {
        StructureSnapshot snapshot = snapshot();
        ProjectSettings settings = settings(
                ProjectSettings.OriginMode.SELECTION_CENTRE,
                true);
        SourceReference source = source(snapshot);
        MarkerInterpretationResult markerResult = new MarkerInterpretationResult(
                snapshot.blocks(),
                List.of(),
                List.of(
                        new MarkerDirective.Origin("origin/ignored", new Vec3d(1, 0, 0), List.of(source)),
                        new MarkerDirective.PointLight(
                                "light/key", "Key", Transform.IDENTITY, Color3d.WHITE, 4.0,
                                OptionalDouble.of(12.0), List.of(source))),
                List.of(),
                List.of());

        var generated = new FullCubeMeshGenerator().generate(
                snapshot, settings, CancellationToken.NONE, ProgressReporter.NONE);
        var scene = SceneAssembler.assemble(
                generated, snapshot, settings, markerResult, new MaterialResolver());

        assertEquals(new Vec3d(-1.0, -1.0, -1.0), scene.originTransform().translation());
        assertEquals(1, scene.materials().size());
        assertTrue(scene.meshes().getFirst().primitives().getFirst().materialId().isPresent());
        assertEquals(1, scene.lights().size());
        assertEquals(1, scene.collisions().size());
        assertEquals(scene.meshes().stream().map(value -> value.stableId()).toList(),
                scene.collisions().getFirst().meshIds());
    }

    @Test
    void markerOriginIsUsedOnlyWhenRequested() {
        StructureSnapshot snapshot = snapshot();
        ProjectSettings settings = settings(ProjectSettings.OriginMode.MARKER_DEFINED, false);
        SourceReference source = source(snapshot);
        MarkerInterpretationResult markerResult = new MarkerInterpretationResult(
                snapshot.blocks(),
                List.of(),
                List.of(new MarkerDirective.Origin(
                        "origin/marker", new Vec3d(1.5, 0.5, 1.0), List.of(source))),
                List.of(),
                List.of());

        var generated = new FullCubeMeshGenerator().generate(
                snapshot, settings, CancellationToken.NONE, ProgressReporter.NONE);
        var scene = SceneAssembler.assemble(
                generated, snapshot, settings, markerResult, new MaterialResolver());

        assertEquals(new Vec3d(-1.5, -0.5, -1.0), scene.originTransform().translation());
        assertTrue(scene.collisions().isEmpty());
    }

    private static StructureSnapshot snapshot() {
        return new StructureSnapshot(
                ApiVersions.STRUCTURE_SNAPSHOT,
                "snapshot/assembler-test",
                new IntSize3(2, 2, 2),
                Optional.of(new BlockPosition(10, 64, -4)),
                List.of(new BlockEntry(
                        new BlockPosition(0, 0, 0),
                        CanonicalIdentifier.parse("minecraft:stone"),
                        Map.of(),
                        Map.of())),
                Map.of());
    }

    private static SourceReference source(StructureSnapshot snapshot) {
        BlockEntry block = snapshot.blocks().getFirst();
        return new SourceReference(
                snapshot.snapshotId(),
                Optional.of(block.relativePosition()),
                Optional.of(block.blockId()),
                Optional.of(CanonicalIdentifier.parse("mcad:test_rule")));
    }

    private static ProjectSettings settings(ProjectSettings.OriginMode originMode, boolean collisionEnabled) {
        ProjectSettings defaults = RuntimeDefaults.projectSettings();
        return new ProjectSettings(
                defaults.schemaVersion(),
                defaults.selection(),
                defaults.geometry(),
                defaults.meshSeparation(),
                defaults.materials(),
                new ProjectSettings.TransformSettings(
                        originMode,
                        defaults.transform().explicitOriginOffset(),
                        defaults.transform().rotationDegrees(),
                        defaults.transform().unitScale(),
                        defaults.transform().targetAxis()),
                new ProjectSettings.MarkerSettings(true, true, false),
                defaults.optimization(),
                defaults.animation(),
                new ProjectSettings.CollisionSettings(
                        collisionEnabled, defaults.collision().defaultKind()),
                defaults.output(),
                defaults.preview(),
                defaults.advanced());
    }
}
