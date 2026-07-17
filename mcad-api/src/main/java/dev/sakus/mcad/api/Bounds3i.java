/*
 * SPDX-License-Identifier: MPL-2.0
 */
package dev.sakus.mcad.api;


public record Bounds3i(BlockPosition minInclusive, BlockPosition maxExclusive) {
    public Bounds3i {
        Checks.notNull(minInclusive, "minInclusive");
        Checks.notNull(maxExclusive, "maxExclusive");
        if (maxExclusive.x() <= minInclusive.x()
                || maxExclusive.y() <= minInclusive.y()
                || maxExclusive.z() <= minInclusive.z()) {
            throw new IllegalArgumentException("maxExclusive must be greater than minInclusive on every axis");
        }
    }

    public IntSize3 size() {
        return new IntSize3(
                Math.subtractExact(maxExclusive.x(), minInclusive.x()),
                Math.subtractExact(maxExclusive.y(), minInclusive.y()),
                Math.subtractExact(maxExclusive.z(), minInclusive.z()));
    }

    public boolean contains(BlockPosition position) {
        Checks.notNull(position, "position");
        return position.x() >= minInclusive.x() && position.x() < maxExclusive.x()
                && position.y() >= minInclusive.y() && position.y() < maxExclusive.y()
                && position.z() >= minInclusive.z() && position.z() < maxExclusive.z();
    }
}
