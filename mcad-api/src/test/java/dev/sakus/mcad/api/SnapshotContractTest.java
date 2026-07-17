/*
 * SPDX-License-Identifier: MPL-2.0
 */
package dev.sakus.mcad.api;


import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class SnapshotContractTest {
    @Test
    void canonicalIdentifiersRejectNonCanonicalInput() {
        assertEquals("minecraft:stone", CanonicalIdentifier.parse("minecraft:stone").toString());
        assertThrows(IllegalArgumentException.class, () -> CanonicalIdentifier.parse("Minecraft:stone"));
        assertThrows(IllegalArgumentException.class, () -> CanonicalIdentifier.parse("stone"));
    }

    @Test
    void snapshotDefensivelyCopiesAndSortsBlocksAndStates() {
        var states = new HashMap<String, String>();
        states.put("waterlogged", "false");
        states.put("axis", "x");
        var blocks = new ArrayList<BlockEntry>();
        blocks.add(new BlockEntry(new BlockPosition(1, 0, 0), CanonicalIdentifier.parse("minecraft:dirt"), Map.of(), Map.of()));
        blocks.add(new BlockEntry(new BlockPosition(0, 0, 0), CanonicalIdentifier.parse("minecraft:stone"), states, Map.of()));

        var snapshot = new StructureSnapshot(
                ApiVersions.STRUCTURE_SNAPSHOT,
                "snapshot:test",
                new IntSize3(2, 1, 1),
                Optional.of(new BlockPosition(-10, -64, -20)),
                blocks,
                Map.of());

        states.put("axis", "z");
        blocks.clear();

        assertEquals(new BlockPosition(0, 0, 0), snapshot.blocks().getFirst().relativePosition());
        assertEquals(List.of("axis", "waterlogged"), List.copyOf(snapshot.blocks().getFirst().stateProperties().keySet()));
        assertEquals("x", snapshot.blocks().getFirst().stateProperties().get("axis"));
        assertThrows(UnsupportedOperationException.class, () -> snapshot.blocks().clear());
        assertThrows(UnsupportedOperationException.class, () -> snapshot.blocks().getFirst().stateProperties().clear());
    }

    @Test
    void snapshotRejectsDuplicateAndOutOfRangePositions() {
        var block = new BlockEntry(
                new BlockPosition(0, 0, 0), CanonicalIdentifier.parse("minecraft:stone"), Map.of(), Map.of());
        assertThrows(IllegalArgumentException.class, () -> new StructureSnapshot(
                ApiVersions.STRUCTURE_SNAPSHOT, "snapshot:duplicate", new IntSize3(1, 1, 1),
                Optional.empty(), List.of(block, block), Map.of()));

        var outside = new BlockEntry(
                new BlockPosition(-1, 0, 0), CanonicalIdentifier.parse("minecraft:stone"), Map.of(), Map.of());
        assertThrows(IllegalArgumentException.class, () -> new StructureSnapshot(
                ApiVersions.STRUCTURE_SNAPSHOT, "snapshot:outside", new IntSize3(1, 1, 1),
                Optional.empty(), List.of(outside), Map.of()));
    }

    @Test
    void boundsAreMaximumExclusive() {
        var bounds = new Bounds3i(new BlockPosition(-1, 2, 3), new BlockPosition(1, 4, 5));
        assertTrue(bounds.contains(new BlockPosition(0, 3, 4)));
        assertEquals(new IntSize3(2, 2, 2), bounds.size());
    }
}
