/*
 * SPDX-License-Identifier: MPL-2.0
 */
package dev.sakus.mcad.minecraft.snapshot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.TreeSet;
import net.minecraft.world.level.block.Blocks;
import org.junit.jupiter.api.Test;

final class FabricClientSnapshotWorldViewTest {
    @Test
    void mapsNegativeAndBoundaryBlockCoordinatesToTheCorrectChunks() {
        assertEquals(-2, FabricClientSnapshotWorldView.chunkCoordinate(-17));
        assertEquals(-1, FabricClientSnapshotWorldView.chunkCoordinate(-16));
        assertEquals(-1, FabricClientSnapshotWorldView.chunkCoordinate(-1));
        assertEquals(0, FabricClientSnapshotWorldView.chunkCoordinate(0));
        assertEquals(0, FabricClientSnapshotWorldView.chunkCoordinate(15));
        assertEquals(1, FabricClientSnapshotWorldView.chunkCoordinate(16));
    }

    @Test
    void mapsMinecraftAirToTheNeutralAirValue() {
        var result = FabricClientSnapshotWorldView.blockData(Blocks.AIR.defaultBlockState());

        assertTrue(result.isAir());
        assertTrue(result.stateProperties().isEmpty());
        assertTrue(result.metadata().isEmpty());
    }

    @Test
    void copiesRegisteredBlockIdentifierAndPropertiesInDeterministicOrder() {
        var result = FabricClientSnapshotWorldView.blockData(Blocks.OAK_STAIRS.defaultBlockState());

        assertEquals("minecraft:oak_stairs", result.blockIdentifier().orElseThrow());
        assertFalse(result.stateProperties().isEmpty());
        assertEquals(
                new ArrayList<>(new TreeSet<>(result.stateProperties().keySet())),
                new ArrayList<>(result.stateProperties().keySet()));
        assertTrue(result.metadata().isEmpty());
        assertThrows(
                UnsupportedOperationException.class,
                () -> result.stateProperties().put("unsupported", "value"));
    }
}
