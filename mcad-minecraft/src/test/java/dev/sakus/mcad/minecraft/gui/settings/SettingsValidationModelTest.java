/*
 * SPDX-License-Identifier: MPL-2.0
 */
package dev.sakus.mcad.minecraft.gui.settings;

import dev.sakus.mcad.api.ApiVersions;
import dev.sakus.mcad.api.ExporterCapabilities;
import dev.sakus.mcad.api.ExporterLimits;
import dev.sakus.mcad.api.ProjectSettings;
import dev.sakus.mcad.minecraft.gui.ExporterCapabilityModel;
import dev.sakus.mcad.minecraft.gui.SettingsSection;
import dev.sakus.mcad.minecraft.gui.SettingsValidationModel;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SettingsValidationModelTest {
    @Test
    void reportsCrossFieldErrorsInDeterministicOrder() {
        ProjectSettings source = ProjectSettingsFixtures.populated();
        ProjectSettings invalid = ProjectSettingsDraft.from(source)
                .withMaterials(new ProjectSettings.MaterialSettings(
                        ProjectSettings.MaterialMode.USER_DEFINED_MAPPING,
                        Optional.empty()))
                .withTransform(new ProjectSettings.TransformSettings(
                        ProjectSettings.OriginMode.MARKER_DEFINED,
                        source.transform().explicitOriginOffset(),
                        source.transform().rotationDegrees(),
                        source.transform().unitScale(),
                        source.transform().targetAxis()))
                .withMarkers(new ProjectSettings.MarkerSettings(false, true, true))
                .value();

        SettingsValidationModel validation = SettingsValidationModel.validate(invalid, Optional.empty());

        assertFalse(validation.valid());
        assertEquals(3, validation.diagnostics().size());
        assertEquals("materials.user_mapping.required", validation.diagnostics().get(0).code());
        assertEquals("transform.marker_origin.requires_markers", validation.diagnostics().get(1).code());
        assertEquals("markers.preview.inactive", validation.diagnostics().get(2).code());
        assertEquals(1, validation.forSection(SettingsSection.MATERIALS).size());
    }

    @Test
    void capabilityLossesAreErrorsWhenLossPolicyIsFail() {
        ProjectSettings settings = capabilitySensitiveSettings(ProjectSettings.LossPolicy.FAIL);
        SettingsValidationModel validation = SettingsValidationModel.validate(
                settings,
                Optional.of(ExporterCapabilityModel.from(noFeatureCapabilities())));

        assertFalse(validation.valid());
        assertEquals(Set.of(
                        "mesh_separation.multiple_meshes.unsupported",
                        "materials.vertex_colours.unsupported",
                        "animation.unsupported",
                        "collision.metadata.unsupported"),
                validation.diagnostics().stream()
                        .map(SettingsValidationModel.Diagnostic::code)
                        .collect(java.util.stream.Collectors.toUnmodifiableSet()));
        assertTrue(validation.diagnostics().stream().allMatch(
                diagnostic -> diagnostic.severity() == SettingsValidationModel.Severity.ERROR));
    }

    @Test
    void capabilityLossesAreWarningsWhenUserAllowsLoss() {
        ProjectSettings settings = capabilitySensitiveSettings(ProjectSettings.LossPolicy.WARN_AND_CONTINUE);
        SettingsValidationModel validation = SettingsValidationModel.validate(
                settings,
                Optional.of(ExporterCapabilityModel.from(noFeatureCapabilities())));

        assertTrue(validation.valid());
        assertEquals(4, validation.diagnostics().size());
        assertTrue(validation.diagnostics().stream().allMatch(
                diagnostic -> diagnostic.severity() == SettingsValidationModel.Severity.WARNING));
    }

    @Test
    void diagnosticCollectionsCannotBeModifiedExternally() {
        SettingsValidationModel validation = SettingsValidationModel.validate(
                ProjectSettingsFixtures.populated(),
                Optional.empty());

        assertThrows(UnsupportedOperationException.class, () -> validation.diagnostics().clear());
    }

    private static ProjectSettings capabilitySensitiveSettings(ProjectSettings.LossPolicy lossPolicy) {
        ProjectSettings source = ProjectSettingsFixtures.populated();
        ProjectSettings.OutputSettings output = new ProjectSettings.OutputSettings(
                source.output().exporterId(),
                source.output().destination(),
                lossPolicy,
                source.output().exporterOptions());
        return ProjectSettingsDraft.from(source)
                .withMaterials(new ProjectSettings.MaterialSettings(
                        ProjectSettings.MaterialMode.VERTEX_COLOURS,
                        Optional.empty()))
                .withOutput(output)
                .value();
    }

    private static ExporterCapabilities noFeatureCapabilities() {
        return new ExporterCapabilities(
                ApiVersions.GENERATED_SCENE,
                ApiVersions.GENERATED_SCENE,
                Set.of(),
                ExporterLimits.unlimited(),
                Set.of("obj"));
    }
}
