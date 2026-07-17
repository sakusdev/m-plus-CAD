/*
 * SPDX-License-Identifier: MPL-2.0
 */
package dev.sakus.mcad.minecraft.gui;

import dev.sakus.mcad.minecraft.gui.settings.SettingsShellController;
import dev.sakus.mcad.minecraft.selection.SelectionController;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Keeps selection validation limits synchronized with the versioned project settings model.
 */
public final class SelectionSettingsBinding implements AutoCloseable {
    private final SelectionController selectionController;
    private final SettingsShellController.Subscription subscription;
    private final AtomicBoolean closed = new AtomicBoolean();

    public SelectionSettingsBinding(
            SettingsShellController settingsController,
            SelectionController selectionController) {
        Objects.requireNonNull(settingsController, "settingsController");
        this.selectionController = Objects.requireNonNull(selectionController, "selectionController");
        apply(settingsController.snapshot());
        subscription = settingsController.subscribe(this::apply);
    }

    @Override
    public void close() {
        if (closed.compareAndSet(false, true)) {
            subscription.close();
        }
    }

    private void apply(SettingsShellController.Snapshot snapshot) {
        if (closed.get()) {
            return;
        }
        selectionController.setMaximumBlockCount(
                snapshot.draft().value().selection().maximumBlockCount());
    }
}
