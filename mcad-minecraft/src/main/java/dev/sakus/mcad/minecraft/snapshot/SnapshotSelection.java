/*
 * SPDX-License-Identifier: MPL-2.0
 */
package dev.sakus.mcad.minecraft.snapshot;

import dev.sakus.mcad.api.BlockPosition;
import dev.sakus.mcad.api.IntSize3;
import java.util.Objects;

/** Minimum-inclusive, maximum-exclusive world selection. */
public record SnapshotSelection(BlockPosition minInclusive, BlockPosition maxExclusive) {
    public SnapshotSelection {
        Objects.requireNonNull(minInclusive, "minInclusive");
        Objects.requireNonNull(maxExclusive, "maxExclusive");
        dimension(minInclusive.x(), maxExclusive.x(), "x");
        dimension(minInclusive.y(), maxExclusive.y(), "y");
        dimension(minInclusive.z(), maxExclusive.z(), "z");
    }

    public IntSize3 size() {
        return new IntSize3(
                dimension(minInclusive.x(), maxExclusive.x(), "x"),
                dimension(minInclusive.y(), maxExclusive.y(), "y"),
                dimension(minInclusive.z(), maxExclusive.z(), "z"));
    }

    public long volume() {
        return size().volume();
    }

    private static int dimension(int min, int max, String axis) {
        long value = (long) max - min;
        if (value <= 0 || value > Integer.MAX_VALUE) {
            throw new IllegalArgumentException(
                    axis + " selection dimension must be in [1, " + Integer.MAX_VALUE + "]: " + value);
        }
        return (int) value;
    }
}
