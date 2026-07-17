/*
 * SPDX-License-Identifier: MPL-2.0
 */
package dev.sakus.mcad.minecraft.gui.settings;

import dev.sakus.mcad.api.ProjectSettings;
import dev.sakus.mcad.minecraft.gui.SelectionSettingsBinding;
import dev.sakus.mcad.minecraft.selection.SelectionController;
import dev.sakus.mcad.minecraft.selection.SelectionPoint;
import dev.sakus.mcad.minecraft.selection.SelectionValidation;
import dev.sakus.mcad.minecraft.selection.TwoPointSelection;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SelectionSettingsBindingTest {
    @Test
    void settingsLimitImmediatelyRevalidatesCurrentSelection() {
        SettingsShellController settings = new SettingsShellController(ProjectSettingsFixtures.populated());
        SelectionController selection = eightBlockSelection();

        try (SelectionSettingsBinding ignored = new SelectionSettingsBinding(settings, selection)) {
            assertEquals(SelectionValidation.Code.VALID, selection.snapshot().validation().code());

            settings.edit(draft -> draft.withSelection(
                    new ProjectSettings.SelectionSettings(7L, false)));

            assertEquals(SelectionValidation.Code.TOO_LARGE, selection.snapshot().validation().code());
        }
    }

    @Test
    void reentrantSettingsUpdateWinsOverOlderInitialSnapshot() {
        SettingsShellController settings = new SettingsShellController(ProjectSettingsFixtures.populated());
        SelectionController selection = eightBlockSelection();
        AtomicBoolean updateOnce = new AtomicBoolean();
        selection.subscribe(snapshot -> {
            if (updateOnce.compareAndSet(false, true)) {
                settings.edit(draft -> draft.withSelection(
                        new ProjectSettings.SelectionSettings(7L, false)));
            }
        });

        try (SelectionSettingsBinding ignored = new SelectionSettingsBinding(settings, selection)) {
            assertEquals(SelectionValidation.Code.TOO_LARGE, selection.snapshot().validation().code());

            settings.edit(draft -> draft.withSelection(
                    new ProjectSettings.SelectionSettings(8L, false)));

            assertEquals(SelectionValidation.Code.VALID, selection.snapshot().validation().code());
        }
    }

    @Test
    void closedBindingStopsFollowingSettingsChanges() {
        SettingsShellController settings = new SettingsShellController(ProjectSettingsFixtures.populated());
        SelectionController selection = eightBlockSelection();
        SelectionSettingsBinding binding = new SelectionSettingsBinding(settings, selection);
        assertEquals(SelectionValidation.Code.VALID, selection.snapshot().validation().code());
        binding.close();

        settings.edit(draft -> draft.withSelection(
                new ProjectSettings.SelectionSettings(7L, false)));

        assertEquals(SelectionValidation.Code.VALID, selection.snapshot().validation().code());
    }

    private static SelectionController eightBlockSelection() {
        SelectionController selection = new SelectionController(1L);
        selection.setCorner(TwoPointSelection.Corner.START, new SelectionPoint(0, 0, 0));
        selection.setCorner(TwoPointSelection.Corner.END, new SelectionPoint(1, 1, 1));
        return selection;
    }
}
