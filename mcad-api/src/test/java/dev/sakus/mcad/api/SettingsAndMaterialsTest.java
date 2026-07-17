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

class SettingsAndMaterialsTest {
    @Test
    void materialValidatesPbrFieldsAndPaths() {
        assertThrows(IllegalArgumentException.class, () -> new MaterialDefinition(
                "material:test", "Test", new Color4d(1, 1, 1, 1), Double.NaN, 1,
                Color3d.BLACK, 0, AlphaMode.OPAQUE, OptionalDouble.empty(), List.of(), Map.of(), List.of()));
        assertThrows(IllegalArgumentException.class, () -> new UserAssetReference("../secret.png", true));
        assertEquals("textures/user.png", new UserAssetReference("textures/user.png", false).projectRelativePath());
    }

    @Test
    void settingsPreserveExplicitSeparationOrder() {
        var separation = new ProjectSettings.MeshSeparationSettings(List.of(
                ProjectSettings.SeparationKey.USER_GROUP,
                ProjectSettings.SeparationKey.BLOCK_IDENTIFIER,
                ProjectSettings.SeparationKey.MATERIAL_IDENTIFIER));
        assertEquals(ProjectSettings.SeparationKey.USER_GROUP, separation.orderedKeys().getFirst());
        assertThrows(UnsupportedOperationException.class, () -> separation.orderedKeys().clear());
        assertThrows(IllegalArgumentException.class, () -> new ProjectSettings.MeshSeparationSettings(List.of(
                ProjectSettings.SeparationKey.BLOCK_IDENTIFIER,
                ProjectSettings.SeparationKey.BLOCK_IDENTIFIER)));
    }

    @Test
    void outputFormatDoesNotOwnOrRewriteOtherSettings() {
        var settings = new ProjectSettings(
                ApiVersions.PROJECT_SETTINGS,
                new ProjectSettings.SelectionSettings(1_000_000, false),
                new ProjectSettings.GeometrySettings(true, true),
                new ProjectSettings.MeshSeparationSettings(List.of(ProjectSettings.SeparationKey.BLOCK_IDENTIFIER)),
                new ProjectSettings.MaterialSettings(ProjectSettings.MaterialMode.NONE, Optional.empty()),
                new ProjectSettings.TransformSettings(ProjectSettings.OriginMode.SELECTION_MINIMUM, Vec3d.ZERO,
                        Vec3d.ZERO, 1.0, ProjectSettings.AxisConvention.INTERNAL_RIGHT_HANDED_Y_UP),
                new ProjectSettings.MarkerSettings(true, true, true),
                new ProjectSettings.OptimizationSettings(false, false, true),
                new ProjectSettings.AnimationSettings(false, 20),
                new ProjectSettings.CollisionSettings(false, CollisionKind.SOLID),
                new ProjectSettings.OutputSettings(CanonicalIdentifier.parse("mcad:obj"), "exports/model.obj",
                        ProjectSettings.LossPolicy.FAIL, Map.of()),
                new ProjectSettings.PreviewSettings(true, true, true),
                new ProjectSettings.AdvancedSettings(2, 512L * 1024 * 1024));

        assertEquals(ProjectSettings.MaterialMode.NONE, settings.materials().mode());
        assertEquals(List.of(ProjectSettings.SeparationKey.BLOCK_IDENTIFIER), settings.meshSeparation().orderedKeys());
    }
}
