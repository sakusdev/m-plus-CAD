/*
 * SPDX-License-Identifier: MPL-2.0
 */
package dev.sakus.mcad.minecraft.snapshot;

import dev.sakus.mcad.api.IntSize3;
import java.util.Objects;

/** Hard limits checked before any world cells are retained. */
public record SnapshotLimits(
        int maxWidth,
        int maxHeight,
        int maxDepth,
        long maxCells,
        int maxRetainedBlocks,
        int progressInterval) {

    public SnapshotLimits {
        positive(maxWidth, "maxWidth");
        positive(maxHeight, "maxHeight");
        positive(maxDepth, "maxDepth");
        positive(maxCells, "maxCells");
        positive(maxRetainedBlocks, "maxRetainedBlocks");
        positive(progressInterval, "progressInterval");
    }

    public static SnapshotLimits defaults() {
        return new SnapshotLimits(512, 512, 512, 16_777_216L, 8_388_608, 1_024);
    }

    public void validate(SnapshotSelection selection) {
        Objects.requireNonNull(selection, "selection");
        IntSize3 size = selection.size();
        if (size.width() > maxWidth || size.height() > maxHeight || size.depth() > maxDepth) {
            throw new SnapshotLimitException(
                    "selection dimensions exceed limits: " + size + " > "
                            + maxWidth + "x" + maxHeight + "x" + maxDepth);
        }
        if (size.volume() > maxCells) {
            throw new SnapshotLimitException(
                    "selection cell count exceeds limit: " + size.volume() + " > " + maxCells);
        }
    }

    private static void positive(long value, String name) {
        if (value <= 0) {
            throw new IllegalArgumentException(name + " must be positive");
        }
    }
}
