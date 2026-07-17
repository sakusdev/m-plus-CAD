/*
 * SPDX-License-Identifier: MPL-2.0
 */
package dev.sakus.mcad.minecraft.selection;

import org.junit.jupiter.api.Test;

import java.math.BigInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SelectionBoundsTest {
    @Test
    void normalizesCornersIndependentlyOnEveryAxis() {
        SelectionBounds bounds = SelectionBounds.between(
                new SelectionPoint(5, -2, 9),
                new SelectionPoint(-3, 4, 1));

        assertEquals(new SelectionPoint(-3, -2, 1), bounds.minInclusive());
        assertEquals(new SelectionPoint(5, 4, 9), bounds.maxInclusive());
        assertEquals(9L, bounds.width());
        assertEquals(7L, bounds.height());
        assertEquals(9L, bounds.depth());
        assertEquals(BigInteger.valueOf(567L), bounds.blockCount());
    }

    @Test
    void calculatesNegativeCoordinateSelectionInclusively() {
        SelectionBounds bounds = SelectionBounds.between(
                new SelectionPoint(-5, -10, -2),
                new SelectionPoint(-3, -10, -2));

        assertEquals(3L, bounds.width());
        assertEquals(1L, bounds.height());
        assertEquals(1L, bounds.depth());
        assertEquals(BigInteger.valueOf(3L), bounds.blockCount());
        assertEquals(-2L, bounds.maxExclusiveX());
    }

    @Test
    void calculatesExtremeVolumeWithoutLongOverflow() {
        SelectionBounds bounds = SelectionBounds.between(
                new SelectionPoint(Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE),
                new SelectionPoint(Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE));

        assertEquals(1L << 32, bounds.width());
        assertEquals(BigInteger.ONE.shiftLeft(96), bounds.blockCount());
    }

    @Test
    void containsUsesInclusiveBlockCoordinates() {
        SelectionBounds bounds = SelectionBounds.between(
                new SelectionPoint(-1, 2, 3),
                new SelectionPoint(1, 4, 5));

        assertTrue(bounds.contains(new SelectionPoint(-1, 2, 3)));
        assertTrue(bounds.contains(new SelectionPoint(1, 4, 5)));
        assertFalse(bounds.contains(new SelectionPoint(2, 4, 5)));
    }

    @Test
    void wireframeUsesMaxExclusiveBlockBoundaryAndStableTwelveEdges() {
        SelectionBounds bounds = SelectionBounds.between(
                new SelectionPoint(-2, 3, 4),
                new SelectionPoint(-1, 3, 4));
        SelectionWireframe wireframe = SelectionWireframe.from(bounds);

        assertEquals(12, wireframe.segments().size());
        assertEquals(
                new SelectionWireframe.Segment(
                        new SelectionWireframe.Point(-2.0, 3.0, 4.0),
                        new SelectionWireframe.Point(0.0, 3.0, 4.0)),
                wireframe.segments().getFirst());
    }
}
