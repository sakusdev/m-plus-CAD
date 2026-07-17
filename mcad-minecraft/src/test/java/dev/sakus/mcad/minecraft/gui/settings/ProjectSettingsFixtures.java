/*
 * SPDX-License-Identifier: MPL-2.0
 */
package dev.sakus.mcad.minecraft.gui.settings;

import dev.sakus.mcad.api.ApiVersions;
import dev.sakus.mcad.api.CanonicalIdentifier;
import dev.sakus.mcad.api.CollisionKind;
import dev.sakus.mcad.api.MetadataValue;
import dev.sakus.mcad.api.ProjectSettings;
import dev.sakus.mcad.api.Vec3d;

import java.util.List;
import java.util.Map;
import java.util.Optional;

final class ProjectSettingsFixtures {
    private ProjectSettingsFixtures() {
    }

    static ProjectSettings populated() {
        return new ProjectSettings(
                ApiVersions.PROJECT_SETTINGS,
                new ProjectSettings.SelectionSettings(65_536L, false),
                new ProjectSettings.GeometrySettings(true, true),
                new ProjectSettings.MeshSeparationSettings(List.of(
                        ProjectSettings.SeparationKey.BLOCK_IDENTIFIER,
                        ProjectSettings.SeparationKey.BLOCK_STATE)),
                new ProjectSettings.MaterialSettings(
                        ProjectSettings.MaterialMode.USER_DEFINED_MAPPING,
                        Optional.of("mapping/default")),
                new ProjectSettings.TransformSettings(
                        ProjectSettings.OriginMode.EXPLICIT_OFFSET,
                        new Vec3d(-1.5, 2.0, 3.25),
                        new Vec3d(0.0, 90.0, -45.0),
                        0.5,
                        ProjectSettings.AxisConvention.RIGHT_HANDED_Z_UP),
                new ProjectSettings.MarkerSettings(true, true, true),
                new ProjectSettings.OptimizationSettings(true, false, true),
                new ProjectSettings.AnimationSettings(true, 24),
                new ProjectSettings.CollisionSettings(true, CollisionKind.TRIGGER),
                new ProjectSettings.OutputSettings(
                        new CanonicalIdentifier("mcad", "gltf"),
                        "exports/model.glb",
                        ProjectSettings.LossPolicy.WARN_AND_CONTINUE,
                        Map.of(
                                new CanonicalIdentifier("mcad", "option/nested"),
                                new MetadataValue.MapValue(Map.of(
                                        new CanonicalIdentifier("mcad", "label"),
                                        new MetadataValue.StringValue("example"),
                                        new CanonicalIdentifier("mcad", "values"),
                                        new MetadataValue.ListValue(List.of(
                                                new MetadataValue.LongValue(7L),
                                                new MetadataValue.DoubleValue(0.25),
                                                new MetadataValue.BooleanValue(true))))))),
                new ProjectSettings.PreviewSettings(true, true, false),
                new ProjectSettings.AdvancedSettings(4, 512L * 1024L * 1024L));
    }

    static ProjectSettings migrationDefaults() {
        ProjectSettings source = populated();
        return new ProjectSettings(
                source.schemaVersion(),
                source.selection(),
                source.geometry(),
                source.meshSeparation(),
                source.materials(),
                source.transform(),
                source.markers(),
                source.optimization(),
                new ProjectSettings.AnimationSettings(false, 30),
                source.collision(),
                source.output(),
                new ProjectSettings.PreviewSettings(false, false, true),
                source.advanced());
    }
}
