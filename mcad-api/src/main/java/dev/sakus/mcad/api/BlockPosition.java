/*
 * SPDX-License-Identifier: MPL-2.0
 */
package dev.sakus.mcad.api;


public record BlockPosition(int x, int y, int z) implements Comparable<BlockPosition> {
    @Override
    public int compareTo(BlockPosition other) {
        int yComparison = Integer.compare(y, other.y);
        if (yComparison != 0) {
            return yComparison;
        }
        int zComparison = Integer.compare(z, other.z);
        return zComparison != 0 ? zComparison : Integer.compare(x, other.x);
    }

    public BlockPosition subtract(BlockPosition other) {
        Checks.notNull(other, "other");
        return new BlockPosition(
                Math.subtractExact(x, other.x),
                Math.subtractExact(y, other.y),
                Math.subtractExact(z, other.z));
    }
}
