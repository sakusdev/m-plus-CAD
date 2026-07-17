/*
 * SPDX-License-Identifier: MPL-2.0
 */
package dev.sakus.mcad.minecraft.selection;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

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

    private record UndirectedSegment(Point first, Point second) {
        private static UndirectedSegment of(Segment segment) {
            return comparePoints(segment.start(), segment.end()) <= 0
                    ? new UndirectedSegment(segment.start(), segment.end())
                    : new UndirectedSegment(segment.end(), segment.start());
        }
    }

    public SelectionWireframe {
        segments = List.copyOf(Objects.requireNonNull(segments, "segments"));
        if (segments.size() != 12) {
            throw new IllegalArgumentException("a rectangular selection wireframe must contain 12 segments");
        }

        Set<UndirectedSegment> uniqueSegments = new HashSet<>();
        Set<Point> vertices = new HashSet<>();
        Map<Point, Integer> degrees = new HashMap<>();
        Set<Double> xCoordinates = new HashSet<>();
        Set<Double> yCoordinates = new HashSet<>();
        Set<Double> zCoordinates = new HashSet<>();

        for (Segment segment : segments) {
            if (!uniqueSegments.add(UndirectedSegment.of(segment))) {
                throw new IllegalArgumentException("wireframe must not contain duplicate segments");
            }
            requireAxisAligned(segment);
            addVertex(segment.start(), vertices, degrees, xCoordinates, yCoordinates, zCoordinates);
            addVertex(segment.end(), vertices, degrees, xCoordinates, yCoordinates, zCoordinates);
        }

        if (vertices.size() != 8) {
            throw new IllegalArgumentException("a rectangular selection wireframe must contain 8 vertices");
        }
        if (xCoordinates.size() != 2 || yCoordinates.size() != 2 || zCoordinates.size() != 2) {
            throw new IllegalArgumentException("wireframe vertices must define exactly two planes per axis");
        }
        if (degrees.values().stream().anyMatch(degree -> degree != 3)) {
            throw new IllegalArgumentException("every rectangular wireframe vertex must have degree 3");
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

    private static void addVertex(
            Point point,
            Set<Point> vertices,
            Map<Point, Integer> degrees,
            Set<Double> xCoordinates,
            Set<Double> yCoordinates,
            Set<Double> zCoordinates) {
        vertices.add(point);
        degrees.merge(point, 1, Integer::sum);
        xCoordinates.add(point.x());
        yCoordinates.add(point.y());
        zCoordinates.add(point.z());
    }

    private static void requireAxisAligned(Segment segment) {
        int changedAxes = 0;
        if (Double.compare(segment.start().x(), segment.end().x()) != 0) {
            changedAxes++;
        }
        if (Double.compare(segment.start().y(), segment.end().y()) != 0) {
            changedAxes++;
        }
        if (Double.compare(segment.start().z(), segment.end().z()) != 0) {
            changedAxes++;
        }
        if (changedAxes != 1) {
            throw new IllegalArgumentException("wireframe segments must be axis-aligned");
        }
    }

    private static int comparePoints(Point left, Point right) {
        int x = Double.compare(left.x(), right.x());
        if (x != 0) {
            return x;
        }
        int y = Double.compare(left.y(), right.y());
        if (y != 0) {
            return y;
        }
        return Double.compare(left.z(), right.z());
    }

    private static void requireFinite(double value, String name) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException(name + " must be finite");
        }
    }
}
