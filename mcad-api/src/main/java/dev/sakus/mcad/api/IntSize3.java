/*
 * SPDX-License-Identifier: MPL-2.0
 */
package dev.sakus.mcad.api;


public record IntSize3(int width, int height, int depth) {
    public IntSize3 {
        Checks.positive(width, "width");
        Checks.positive(height, "height");
        Checks.positive(depth, "depth");
        Math.multiplyExact(Math.multiplyExact((long) width, height), depth);
    }

    public long volume() {
        return Math.multiplyExact(Math.multiplyExact((long) width, height), depth);
    }

    public boolean contains(BlockPosition position) {
        Checks.notNull(position, "position");
        return position.x() >= 0 && position.x() < width
                && position.y() >= 0 && position.y() < height
                && position.z() >= 0 && position.z() < depth;
    }
}
