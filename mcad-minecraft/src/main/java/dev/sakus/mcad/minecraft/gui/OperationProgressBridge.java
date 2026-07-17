/*
 * SPDX-License-Identifier: MPL-2.0
 */
package dev.sakus.mcad.minecraft.gui;

import dev.sakus.mcad.api.CancellationToken;
import dev.sakus.mcad.api.ProgressReporter;
import dev.sakus.mcad.api.ProgressUpdate;

import java.util.Objects;
import java.util.OptionalLong;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Connects neutral worker progress/cancellation contracts to the GUI view model.
 */
public final class OperationProgressBridge implements ProgressReporter, CancellationToken {
    private final OperationProgressModel model;
    private final AtomicBoolean cancellationRequested = new AtomicBoolean();

    public OperationProgressBridge(OperationProgressModel model) {
        this.model = Objects.requireNonNull(model, "model");
    }

    public OperationProgressModel.Snapshot begin(
            String phase,
            OptionalLong total,
            String message,
            boolean cancellable) {
        cancellationRequested.set(false);
        return model.begin(phase, total, message, cancellable, () -> cancellationRequested.set(true));
    }

    @Override
    public void report(ProgressUpdate update) {
        Objects.requireNonNull(update, "update");
        if (cancellationRequested.get()) {
            return;
        }
        model.report(update.phase(), update.completed(), update.total(), update.message());
    }

    @Override
    public boolean isCancellationRequested() {
        return cancellationRequested.get();
    }

    public OperationProgressModel.Snapshot succeed(String message) {
        return model.succeed(Objects.requireNonNull(message, "message"));
    }

    public OperationProgressModel.Snapshot fail(String message) {
        return model.fail(message);
    }

    public OperationProgressModel.Snapshot cancelled(String message) {
        cancellationRequested.set(true);
        return model.cancelled(Objects.requireNonNull(message, "message"));
    }
}
