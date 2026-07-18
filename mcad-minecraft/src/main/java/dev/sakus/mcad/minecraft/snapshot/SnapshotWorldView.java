/*
 * SPDX-License-Identifier: MPL-2.0
 */
package dev.sakus.mcad.minecraft.snapshot;

/** Game-thread-only world access boundary implemented by the Fabric adapter. */
public interface SnapshotWorldView {
    /** Rejects calls made outside the loader-approved world-read thread. */
    void assertReadThread();

    /** Returns whether the client has the requested world cell loaded. */
    boolean isLoaded(int worldX, int worldY, int worldZ);

    /** Copies one cell into neutral values; Minecraft objects must not be returned. */
    SnapshotBlockData readBlock(int worldX, int worldY, int worldZ);
}
