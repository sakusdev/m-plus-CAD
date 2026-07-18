/*
 * SPDX-License-Identifier: MPL-2.0
 */
package dev.sakus.mcad.minecraft.snapshot;

import dev.sakus.mcad.api.BlockPosition;

/** Indicates that the client does not currently have all selected cells loaded. */
public final class UnloadedSelectionException extends IllegalStateException {
    private static final long serialVersionUID = 1L;
    private final int worldX;
    private final int worldY;
    private final int worldZ;

    public UnloadedSelectionException(BlockPosition worldPosition) {
        super("selection contains an unloaded cell at " + worldPosition);
        worldX = worldPosition.x();
        worldY = worldPosition.y();
        worldZ = worldPosition.z();
    }

    public BlockPosition worldPosition() {
        return new BlockPosition(worldX, worldY, worldZ);
    }
}
