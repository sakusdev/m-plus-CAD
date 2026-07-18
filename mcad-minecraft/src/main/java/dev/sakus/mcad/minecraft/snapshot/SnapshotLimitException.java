/*
 * SPDX-License-Identifier: MPL-2.0
 */
package dev.sakus.mcad.minecraft.snapshot;

/** Indicates that a selection exceeds configured snapshot limits. */
public final class SnapshotLimitException extends IllegalArgumentException {
    private static final long serialVersionUID = 1L;

    public SnapshotLimitException(String message) {
        super(message);
    }
}
