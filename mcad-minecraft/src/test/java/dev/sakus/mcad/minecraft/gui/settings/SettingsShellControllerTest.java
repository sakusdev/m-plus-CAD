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
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SettingsShellControllerTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void changesNavigationWithoutMutatingSettings() {
        ProjectSettings initial = ProjectSettingsFixtures.populated();
        SettingsShellController controller = new SettingsShellController(initial);

        SettingsShellController.Snapshot snapshot = controller.selectSection(SettingsSection.MATERIALS);

        assertEquals(1L, snapshot.revision());
        assertEquals(SettingsSection.MATERIALS, snapshot.activeSection());
        assertEquals(initial, snapshot.draft().value());
        assertTrue(snapshot.validation().valid());
    }

    @Test
    void revisionAdvancesOnlyForObservableStateChanges() {
        ProjectSettings initial = ProjectSettingsFixtures.populated();
        SettingsShellController controller = new SettingsShellController(initial);
        ExporterCapabilities capabilities = new ExporterCapabilities(
                ApiVersions.GENERATED_SCENE,
                ApiVersions.GENERATED_SCENE,
                Set.of(ExporterFeature.MULTIPLE_MESHES),
                ExporterLimits.unlimited(),
                Set.of("obj"));

        assertEquals(0L, controller.snapshot().revision());
        assertEquals(0L, controller.selectSection(SettingsSection.SELECTION).revision());
        assertEquals(1L, controller.selectSection(SettingsSection.OUTPUT).revision());
        assertEquals(1L, controller.edit(draft -> draft).revision());
        assertEquals(2L, controller.edit(draft -> draft.withSelection(
                new ProjectSettings.SelectionSettings(1234L, false))).revision());
        assertEquals(3L, controller.selectExporter(
                new CanonicalIdentifier("mcad", "obj"), capabilities).revision());
        assertEquals(3L, controller.selectExporter(
                new CanonicalIdentifier("mcad", "obj"), capabilities).revision());
        assertEquals(4L, controller.clearExporterCapabilities().revision());
        assertEquals(4L, controller.clearExporterCapabilities().revision());
    }

    @Test
    void reentrantUpdatesAreDeliveredAfterCurrentSnapshotToEveryListener() {
        SettingsShellController controller = new SettingsShellController(ProjectSettingsFixtures.populated());
        List<String> events = new ArrayList<>();
        controller.subscribe(snapshot -> {
            events.add("first-" + snapshot.revision());
            if (snapshot.revision() == 1L) {
                controller.edit(draft -> draft.withSelection(
                        new ProjectSettings.SelectionSettings(1234L, false)));
            }
        });
        controller.subscribe(snapshot -> events.add("second-" + snapshot.revision()));

        controller.selectSection(SettingsSection.OUTPUT);

        assertEquals(List.of("first-1", "second-1", "first-2", "second-2"), events);
    }

    @Test
    void oneListenerFailureDoesNotPreventOtherListenersOrStateUpdate() {
        SettingsShellController controller = new SettingsShellController(ProjectSettingsFixtures.populated());
        List<Long> received = new ArrayList<>();
        controller.subscribe(snapshot -> {
            throw new IllegalStateException("listener failed");
        });
        controller.subscribe(snapshot -> received.add(snapshot.revision()));

        assertThrows(IllegalStateException.class,
                () -> controller.selectSection(SettingsSection.OUTPUT));

        assertEquals(1L, controller.snapshot().revision());
        assertEquals(SettingsSection.OUTPUT, controller.snapshot().activeSection());
        assertEquals(List.of(1L), received);
    }

    @Test
    void selectingExporterUpdatesOnlyIdCapabilityStateAndValidation() {
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
        assertTrue(snapshot.validation().valid());
        assertEquals(Set.of("animation.unsupported", "collision.metadata.unsupported"),
                snapshot.validation().diagnostics().stream()
                        .map(diagnostic -> diagnostic.code())
                        .collect(java.util.stream.Collectors.toUnmodifiableSet()));
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
        assertTrue(controller.snapshot().validation().valid());
    }
}
