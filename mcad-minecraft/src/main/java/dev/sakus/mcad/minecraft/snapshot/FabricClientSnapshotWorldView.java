/*
 * SPDX-License-Identifier: MPL-2.0
 */
package dev.sakus.mcad.minecraft.snapshot;

import dev.sakus.mcad.api.BlockPosition;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Objects;
import java.util.TreeMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.chunk.status.ChunkStatus;

/**
 * Fabric/Minecraft 26.2 client adapter that copies live block states into neutral snapshot values.
 *
 * <p>The adapter never retains a {@link BlockState}, chunk, block entity, or other Minecraft object
 * in the resulting {@link SnapshotBlockData}. Calls are restricted to the active client's packet
 * processor thread and loaded FULL chunks are queried without creating or requesting chunks.
 */
public final class FabricClientSnapshotWorldView implements SnapshotWorldView {
    private static final int CHUNK_SIZE = 16;

    private final Minecraft minecraft;
    private final ClientLevel level;

    public FabricClientSnapshotWorldView(Minecraft minecraft, ClientLevel level) {
        this.minecraft = Objects.requireNonNull(minecraft, "minecraft");
        this.level = Objects.requireNonNull(level, "level");
    }

    /** Creates an adapter for the client's currently active level. */
    public static FabricClientSnapshotWorldView current(Minecraft minecraft) {
        Objects.requireNonNull(minecraft, "minecraft");
        return new FabricClientSnapshotWorldView(
                minecraft,
                Objects.requireNonNull(minecraft.level, "minecraft level is not available"));
    }

    @Override
    public void assertReadThread() {
        if (!minecraft.packetProcessor().isSameThread()) {
            throw new IllegalStateException(
                    "Minecraft world snapshot reads must run on the client packet processor thread");
        }
        if (minecraft.level != level) {
            throw new IllegalStateException("snapshot world is no longer the active client level");
        }
    }

    @Override
    public boolean isLoaded(int worldX, int worldY, int worldZ) {
        assertReadThread();
        if (level.isOutsideBuildHeight(worldY)) {
            return false;
        }
        int chunkX = chunkCoordinate(worldX);
        int chunkZ = chunkCoordinate(worldZ);
        return level.getChunk(chunkX, chunkZ, ChunkStatus.FULL, false) != null;
    }

    @Override
    public SnapshotBlockData readBlock(int worldX, int worldY, int worldZ) {
        assertReadThread();
        if (!isLoaded(worldX, worldY, worldZ)) {
            throw new UnloadedSelectionException(new BlockPosition(worldX, worldY, worldZ));
        }
        return blockData(level.getBlockState(new BlockPos(worldX, worldY, worldZ)));
    }

    static int chunkCoordinate(int blockCoordinate) {
        return Math.floorDiv(blockCoordinate, CHUNK_SIZE);
    }

    static SnapshotBlockData blockData(BlockState state) {
        Objects.requireNonNull(state, "state");
        if (state.isAir()) {
            return SnapshotBlockData.air();
        }
        var identifier = Objects.requireNonNull(
                BuiltInRegistries.BLOCK.getKey(state.getBlock()),
                "block is not registered");
        return SnapshotBlockData.block(identifier.toString(), stateProperties(state), Map.of());
    }

    static NavigableMap<String, String> stateProperties(BlockState state) {
        Objects.requireNonNull(state, "state");
        var values = new TreeMap<String, String>();
        for (var entry : state.getValues().entrySet()) {
            String propertyName = entry.getKey().getName();
            String previous = values.put(propertyName, propertyValueName(entry.getKey(), entry.getValue()));
            if (previous != null) {
                throw new IllegalStateException("duplicate Minecraft state property name: " + propertyName);
            }
        }
        return values;
    }

    private static String propertyValueName(Property<?> property, Comparable<?> value) {
        return propertyValueNameCaptured(property, value);
    }

    private static <T extends Comparable<T>> String propertyValueNameCaptured(
            Property<T> property,
            Comparable<?> value) {
        return property.getName(property.getValueClass().cast(value));
    }
}
