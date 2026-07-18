/*
 * SPDX-License-Identifier: MPL-2.0
 */
package dev.sakus.mcad.minecraft.snapshot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.sakus.mcad.api.BlockPosition;
import dev.sakus.mcad.api.CancellationToken;
import dev.sakus.mcad.api.ProgressReporter;
import java.util.Map;
import org.junit.jupiter.api.Test;

final class SnapshotCaptureFailureTest {
    @Test
    void progressReporterFailureTerminatesCaptureInsteadOfLeavingItRunning() {
        SnapshotCaptureSession session = new WorldSnapshotAdapter().beginCapture(
                new ControllableWorld(),
                selection(0, 0, 0, 1, 1, 1),
                SnapshotLimits.defaults(),
                SnapshotOptions.defaults(),
                update -> {
                    throw new IllegalStateException("progress reporter failed");
                },
                CancellationToken.NONE);

        assertThrows(IllegalStateException.class, () -> session.step(1));

        assertEquals(SnapshotCaptureStatus.FAILED, session.status());
        assertThrows(IllegalStateException.class, session::snapshot);
        assertThrows(IllegalStateException.class, () -> session.step(1));
    }

    @Test
    void threadFailureAfterPartialCaptureDiscardsRetainedData() {
        var world = new ControllableWorld();
        SnapshotCaptureSession session = new WorldSnapshotAdapter().beginCapture(
                world,
                selection(0, 0, 0, 2, 1, 1),
                SnapshotLimits.defaults(),
                SnapshotOptions.defaults(),
                ProgressReporter.NONE,
                CancellationToken.NONE);

        assertEquals(SnapshotCaptureStatus.RUNNING, session.step(1));
        assertEquals(1, session.completedCells());
        world.allowReadThread = false;

        assertThrows(IllegalStateException.class, () -> session.step(1));

        assertEquals(SnapshotCaptureStatus.FAILED, session.status());
        assertThrows(IllegalStateException.class, session::snapshot);
        assertThrows(IllegalStateException.class, () -> session.step(1));
    }

    private static SnapshotSelection selection(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        return new SnapshotSelection(new BlockPosition(minX, minY, minZ), new BlockPosition(maxX, maxY, maxZ));
    }

    private static final class ControllableWorld implements SnapshotWorldView {
        private boolean allowReadThread = true;

        @Override
        public void assertReadThread() {
            if (!allowReadThread) {
                throw new IllegalStateException("not on the client read thread");
            }
        }

        @Override
        public boolean isLoaded(int worldX, int worldY, int worldZ) {
            return true;
        }

        @Override
        public SnapshotBlockData readBlock(int worldX, int worldY, int worldZ) {
            return SnapshotBlockData.block("minecraft:stone", Map.of(), Map.of());
        }
    }
}
