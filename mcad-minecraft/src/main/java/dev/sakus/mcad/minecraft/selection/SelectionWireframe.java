/*
 * SPDX-License-Identifier: MPL-2.0
 */
package dev.sakus.mcad.minecraft.selection;

import java.util.List;
import java.util.Objects;

/**
 * Deterministic renderer-neutral line geometry for the selected block volume.
 */
public record SelectionWireframe(List<Segment> segments) {
    public record Point(double x, double y, double z) {
        public Point {
            requireFinite(x, "x");
            requireFinite(y, "y");
            requireFinite(z, "z");
        }
    }

    public record Segment(Point start, Point end) {
        public Segment {
            Objects.requireNonNull(start, "start");
            Objects.requireNonNull(end, "end");
            if (start.equals(end)) {
                throw new IllegalArgumentException("wireframe segment must have non-zero length");
            }
        }
    }

    public SelectionWireframe {
        segments = List.copyOf(Objects.requireNonNull(segments, "segments"));
        if (segments.size() != 12) {
            throw new IllegalArgumentException("a rectangular selection wireframe must contain 12 segments");
        }
    }

    public static SelectionWireframe from(SelectionBounds bounds) {
        Objects.requireNonNull(bounds, "bounds");
        double minX = bounds.minInclusive().x();
        double minY = bounds.minInclusive().y();
        double minZ = bounds.minInclusive().z();
        double maxX = bounds.maxExclusiveX();
        double maxY = bounds.maxExclusiveY();
        double maxZ = bounds.maxExclusiveZ();

        Point p000 = new Point(minX, minY, minZ);
        Point p100 = new Point(maxX, minY, minZ);
        Point p101 = new Point(maxX, minY, maxZ);
        Point p001 = new Point(minX, minY, maxZ);
        Point p010 = new Point(minX, maxY, minZ);
        Point p110 = new Point(maxX, maxY, minZ);
        Point p111 = new Point(maxX, maxY, maxZ);
        Point p011 = new Point(minX, maxY, maxZ);

        return new SelectionWireframe(List.of(
                new Segment(p000, p100),
                new Segment(p100, p101),
                new Segment(p101, p001),
                new Segment(p001, p000),
                new Segment(p010, p110),
                new Segment(p110, p111),
                new Segment(p111, p011),
                new Segment(p011, p010),
                new Segment(p000, p010),
                new Segment(p100, p110),
                new Segment(p101, p111),
                new Segment(p001, p011)));
    }

    private static void requireFinite(double value, String name) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException(name + " must be finite");
        }
    }
}
