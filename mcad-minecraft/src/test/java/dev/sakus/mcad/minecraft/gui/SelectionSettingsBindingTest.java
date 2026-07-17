/*
 * SPDX-License-Identifier: MPL-2.0
 */
package dev.sakus.mcad.minecraft.gui;

import dev.sakus.mcad.api.ProjectSettings;
import dev.sakus.mcad.minecraft.gui.settings.ProjectSettingsDraft;
import dev.sakus.mcad.minecraft.gui.settings.ProjectSettingsFixtures;
import dev.sakus.mcad.minecraft.gui.settings.SettingsShellController;
import dev.sakus.mcad.minecraft.selection.SelectionController;
import dev.sakus.mcad.minecraft.selection.SelectionPoint;
import dev.sakus.mcad.minecraft.selection.SelectionValidation;
import dev.sakus.mcad.minecraft.selection.TwoPointSelection;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SelectionSettingsBindingTest {
    @Test
    void settingsLimitImmediatelyRevalidatesCurrentSelection() {
        SettingsShellController settings = new SettingsShellController(ProjectSettingsFixtures.populated());
        SelectionController selection = new SelectionController(1L);
        selection.setCorner(TwoPointSelection.Corner.START, new SelectionPoint(0, 0, 0));
        selection.setCorner(TwoPointSelection.Corner.END, new SelectionPoint(1, 1, 1));

        try (SelectionSettingsBinding ignored = new SelectionSettingsBinding(settings, selection)) {
            assertEquals(SelectionValidation.Code.VALID, selection.snapshot().validation().code());

            settings.edit(draft -> draft.withSelection(
                    new ProjectSettings.SelectionSettings(7L, false)));

            assertEquals(SelectionValidation.Code.TOO_LARGE, selection.snapshot().validation().code());
        }
    }

    @Test
    void closedBindingStopsFollowingSettingsChanges() {
        SettingsShellController settings = new SettingsShellController(ProjectSettingsFixtures.populated());
        SelectionController selection = new SelectionController(1L);
        SelectionSettingsBinding binding = new SelectionSettingsBinding(settings, selection);
        binding.close();

        settings.edit(draft -> draft.withSelection(
                new ProjectSettings.SelectionSettings(10L, false)));

        assertEquals(ProjectSettingsFixtures.populated().selection().maximumBlockCount(),
                selection.snapshot().validation().bounds().isEmpty()
                        ? ProjectSettingsFixtures.populated().selection().maximumBlockCount()
                        : -1L);
    }
}
