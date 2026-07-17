/*
 * SPDX-License-Identifier: MPL-2.0
 */
package dev.sakus.mcad.minecraft.gui.settings;

import dev.sakus.mcad.api.ApiVersions;
import dev.sakus.mcad.api.CanonicalIdentifier;
import dev.sakus.mcad.api.ExporterCapabilities;
import dev.sakus.mcad.api.ExporterFeature;
import dev.sakus.mcad.api.ExporterLimits;
import dev.sakus.mcad.api.ProjectSettings;
import dev.sakus.mcad.minecraft.gui.ExporterCapabilityModel;
import dev.sakus.mcad.minecraft.gui.SettingsSection;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SettingsShellControllerTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void changesNavigationWithoutMutatingSettings() {
        ProjectSettings initial = ProjectSettingsFixtures.populated();
        SettingsShellController controller = new SettingsShellController(initial);

        SettingsShellController.Snapshot snapshot = controller.selectSection(SettingsSection.MATERIALS);

        assertEquals(SettingsSection.MATERIALS, snapshot.activeSection());
        assertEquals(initial, snapshot.draft().value());
    }

    @Test
    void selectingExporterUpdatesOnlyIdAndCapabilityState() {
        ProjectSettings initial = ProjectSettingsFixtures.populated();
        SettingsShellController controller = new SettingsShellController(initial);
        ExporterCapabilities capabilities = new ExporterCapabilities(
                ApiVersions.GENERATED_SCENE,
                ApiVersions.GENERATED_SCENE,
                Set.of(ExporterFeature.MULTIPLE_MESHES),
                ExporterLimits.unlimited(),
                Set.of("obj"));

        SettingsShellController.Snapshot snapshot = controller.selectExporter(
                new CanonicalIdentifier("mcad", "obj"),
                capabilities);
        ProjectSettings updated = snapshot.draft().value();

        assertEquals(new CanonicalIdentifier("mcad", "obj"), updated.output().exporterId());
        assertEquals(initial.output().destination(), updated.output().destination());
        assertEquals(initial.output().lossPolicy(), updated.output().lossPolicy());
        assertEquals(initial.output().exporterOptions(), updated.output().exporterOptions());
        assertEquals(initial.selection(), updated.selection());
        assertTrue(snapshot.exporterCapabilities().isPresent());
        assertTrue(snapshot.exporterCapabilities().orElseThrow()
                .availability(ExporterCapabilityModel.Control.MULTIPLE_MESHES).enabled());
        assertFalse(snapshot.exporterCapabilities().orElseThrow()
                .availability(ExporterCapabilityModel.Control.ANIMATION).enabled());
    }

    @Test
    void persistsAndReloadsCurrentDraft() throws IOException {
        ProjectSettings initial = ProjectSettingsFixtures.populated();
        SettingsShellController controller = new SettingsShellController(initial);
        ProjectSettingsStore store = new ProjectSettingsStore(
                temporaryDirectory,
                new ProjectSettingsCodec());
        controller.edit(draft -> draft.withSelection(
                new ProjectSettings.SelectionSettings(1234L, true)));

        controller.save(store, Path.of("project.mcad-settings"));
        controller.edit(draft -> draft.withSelection(
                new ProjectSettings.SelectionSettings(9999L, false)));
        DecodedProjectSettings decoded = controller.load(
                store,
                Path.of("project.mcad-settings"),
                ProjectSettingsFixtures.migrationDefaults());

        assertEquals(1234L, decoded.settings().selection().maximumBlockCount());
        assertTrue(decoded.settings().selection().preserveEmptyCells());
        assertEquals(decoded.settings(), controller.snapshot().draft().value());
        assertTrue(controller.snapshot().exporterCapabilities().isEmpty());
    }
}
