/*
 * SPDX-License-Identifier: MPL-2.0
 */
package dev.sakus.mcad.minecraft.gui;

import dev.sakus.mcad.minecraft.gui.settings.SettingsShellController;
import dev.sakus.mcad.minecraft.selection.SelectionController;

import java.util.Objects;

/**
 * Keeps selection validation limits synchronized with the versioned project settings model.
 */
public final class SelectionSettingsBinding implements AutoCloseable {
    private final Object lock = new Object();
    private final SelectionController selectionController;
    private final SettingsShellController.Subscription subscription;
    private boolean closed;
    private long appliedRevision = -1L;

    public SelectionSettingsBinding(
            SettingsShellController settingsController,
            SelectionController selectionController) {
        Objects.requireNonNull(settingsController, "settingsController");
        this.selectionController = Objects.requireNonNull(selectionController, "selectionController");
        subscription = settingsController.subscribe(this::apply);
        apply(settingsController.snapshot());
    }

    @Override
    public void close() {
        synchronized (lock) {
            if (closed) {
                return;
            }
            closed = true;
        }
        subscription.close();
    }

    private void apply(SettingsShellController.Snapshot snapshot) {
        Objects.requireNonNull(snapshot, "snapshot");
        synchronized (lock) {
            if (closed || snapshot.revision() <= appliedRevision) {
                return;
            }
            appliedRevision = snapshot.revision();
            selectionController.setMaximumBlockCount(
                    snapshot.draft().value().selection().maximumBlockCount());
        }
    }
}
