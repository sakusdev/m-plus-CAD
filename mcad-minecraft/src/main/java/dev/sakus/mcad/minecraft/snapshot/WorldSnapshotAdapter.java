/*
 * SPDX-License-Identifier: MPL-2.0
 */
package dev.sakus.mcad.minecraft.snapshot;

import dev.sakus.mcad.api.CancellationToken;
import dev.sakus.mcad.api.ProgressReporter;
import dev.sakus.mcad.api.StructureSnapshot;
import java.util.Objects;

/** Creates immutable neutral snapshots from a game-thread-confined world view. */
public final class WorldSnapshotAdapter {
    public SnapshotCaptureSession beginCapture(
            SnapshotWorldView world,
            SnapshotSelection selection,
            SnapshotLimits limits,
            SnapshotOptions options,
            ProgressReporter progressReporter,
            CancellationToken cancellationToken) {
        return new SnapshotCaptureSession(
                Objects.requireNonNull(world, "world"),
                Objects.requireNonNull(selection, "selection"),
                Objects.requireNonNull(limits, "limits"),
                Objects.requireNonNull(options, "options"),
                Objects.requireNonNull(progressReporter, "progressReporter"),
                Objects.requireNonNull(cancellationToken, "cancellationToken"));
    }

    /** Convenience method for tests or already-budgeted callers. */
    public StructureSnapshot capture(
            SnapshotWorldView world,
            SnapshotSelection selection,
            SnapshotLimits limits,
            SnapshotOptions options,
            ProgressReporter progressReporter,
            CancellationToken cancellationToken,
            int batchSize) {
        SnapshotCaptureSession session = beginCapture(
                world, selection, limits, options, progressReporter, cancellationToken);
        while (session.status() == SnapshotCaptureStatus.RUNNING) {
            session.step(batchSize);
        }
        return session.snapshot();
    }
}
