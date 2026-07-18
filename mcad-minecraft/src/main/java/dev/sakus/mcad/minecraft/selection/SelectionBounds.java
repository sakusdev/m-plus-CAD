/*
 * SPDX-License-Identifier: MPL-2.0
 */
package dev.sakus.mcad.minecraft.selection;

import java.math.BigInteger;
import java.util.Objects;

/**
 * Normalized inclusive block bounds for a two-corner selection.
 */
public record SelectionBounds(SelectionPoint minInclusive, SelectionPoint maxInclusive) {
    public SelectionBounds {
        Objects.requireNonNull(minInclusive, "minInclusive");
        Objects.requireNonNull(maxInclusive, "maxInclusive");
        if (minInclusive.x() > maxInclusive.x()
                || minInclusive.y() > maxInclusive.y()
                || minInclusive.z() > maxInclusive.z()) {
            throw new IllegalArgumentException("minInclusive must not exceed maxInclusive");
        }
    }

    public static SelectionBounds between(SelectionPoint first, SelectionPoint second) {
        Objects.requireNonNull(first, "first");
        Objects.requireNonNull(second, "second");
        return new SelectionBounds(
                new SelectionPoint(
                        Math.min(first.x(), second.x()),
                        Math.min(first.y(), second.y()),
                        Math.min(first.z(), second.z())),
                new SelectionPoint(
                        Math.max(first.x(), second.x()),
                        Math.max(first.y(), second.y()),
                        Math.max(first.z(), second.z())));
    }

    public long width() {
        return inclusiveLength(minInclusive.x(), maxInclusive.x());
    }

    public long height() {
        return inclusiveLength(minInclusive.y(), maxInclusive.y());
    }

    public long depth() {
        return inclusiveLength(minInclusive.z(), maxInclusive.z());
    }

    public BigInteger blockCount() {
        return BigInteger.valueOf(width())
                .multiply(BigInteger.valueOf(height()))
                .multiply(BigInteger.valueOf(depth()));
    }

    public long maxExclusiveX() {
        return (long) maxInclusive.x() + 1L;
    }

    public long maxExclusiveY() {
        return (long) maxInclusive.y() + 1L;
    }

    public long maxExclusiveZ() {
        return (long) maxInclusive.z() + 1L;
    }

    public boolean contains(SelectionPoint point) {
        Objects.requireNonNull(point, "point");
        return point.x() >= minInclusive.x() && point.x() <= maxInclusive.x()
                && point.y() >= minInclusive.y() && point.y() <= maxInclusive.y()
                && point.z() >= minInclusive.z() && point.z() <= maxInclusive.z();
    }

    private static long inclusiveLength(int minimum, int maximum) {
        return (long) maximum - minimum + 1L;
    }
}
