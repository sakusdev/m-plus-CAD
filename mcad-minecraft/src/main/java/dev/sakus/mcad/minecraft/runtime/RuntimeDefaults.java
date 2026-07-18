/*
 * SPDX-License-Identifier: MPL-2.0
 */
package dev.sakus.mcad.minecraft.runtime;

import dev.sakus.mcad.api.ApiVersions;
import dev.sakus.mcad.api.CanonicalIdentifier;
import dev.sakus.mcad.api.CollisionKind;
import dev.sakus.mcad.api.ProjectSettings;
import dev.sakus.mcad.api.Vec3d;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Safe project-owned defaults. No Minecraft texture or model asset is referenced. */
public final class RuntimeDefaults {
    private RuntimeDefaults() {
    }

    public static ProjectSettings projectSettings() {
        return new ProjectSettings(
                ApiVersions.PROJECT_SETTINGS,
                new ProjectSettings.SelectionSettings(262_144L, false),
                new ProjectSettings.GeometrySettings(true, true),
                new ProjectSettings.MeshSeparationSettings(List.of(
                        ProjectSettings.SeparationKey.BLOCK_IDENTIFIER)),
                new ProjectSettings.MaterialSettings(
                        ProjectSettings.MaterialMode.DETERMINISTIC_IDENTIFICATION_COLOURS,
                        Optional.empty()),
                new ProjectSettings.TransformSettings(
                        ProjectSettings.OriginMode.SELECTION_MINIMUM,
                        Vec3d.ZERO,
                        Vec3d.ZERO,
                        1.0,
                        ProjectSettings.AxisConvention.INTERNAL_RIGHT_HANDED_Y_UP),
                new ProjectSettings.MarkerSettings(false, true, false),
                new ProjectSettings.OptimizationSettings(false, false, true),
                new ProjectSettings.AnimationSettings(false, 30),
                new ProjectSettings.CollisionSettings(false, CollisionKind.SOLID),
                new ProjectSettings.OutputSettings(
                        CanonicalIdentifier.parse("mcad:gltf"),
                        "exports/model.glb",
                        ProjectSettings.LossPolicy.WARN_AND_CONTINUE,
                        Map.of()),
                new ProjectSettings.PreviewSettings(true, true, false),
                new ProjectSettings.AdvancedSettings(
                        Math.max(1, Math.min(4, Runtime.getRuntime().availableProcessors())),
                        512L * 1024L * 1024L));
    }
}
