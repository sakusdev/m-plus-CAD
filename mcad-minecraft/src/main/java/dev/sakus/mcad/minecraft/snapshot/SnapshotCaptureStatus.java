/*
 * SPDX-License-Identifier: MPL-2.0
 */
package dev.sakus.mcad.minecraft.snapshot;

/** Lifecycle state of a staged snapshot capture. */
public enum SnapshotCaptureStatus {
    RUNNING,
    COMPLETE,
    CANCELLED,
    FAILED
}
