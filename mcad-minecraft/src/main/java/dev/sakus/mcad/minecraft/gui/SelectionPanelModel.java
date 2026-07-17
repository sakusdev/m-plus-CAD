/*
 * SPDX-License-Identifier: MPL-2.0
 */
package dev.sakus.mcad.minecraft.gui;

import dev.sakus.mcad.minecraft.selection.SelectionBounds;
import dev.sakus.mcad.minecraft.selection.SelectionController;
import dev.sakus.mcad.minecraft.selection.SelectionPoint;
import dev.sakus.mcad.minecraft.selection.SelectionValidation;

import java.math.BigInteger;
import java.util.Objects;
import java.util.Optional;

/**
 * Immutable values required by the Selection section and HUD overlay.
 */
public record SelectionPanelModel(
        long revision,
        Optional<SelectionPoint> start,
        Optional<SelectionPoint> end,
        Optional<Dimensions> dimensions,
        SelectionValidation validation) {

    public record Dimensions(long width, long height, long depth, BigInteger blockCount) {
        public Dimensions {
            if (width <= 0L || height <= 0L || depth <= 0L) {
                throw new IllegalArgumentException("selection dimensions must be positive");
            }
            Objects.requireNonNull(blockCount, "blockCount");
            if (blockCount.signum() <= 0) {
                throw new IllegalArgumentException("blockCount must be positive");
            }
        }
    }

    public SelectionPanelModel {
        if (revision < 0L) {
            throw new IllegalArgumentException("revision must be non-negative");
        }
        start = Objects.requireNonNull(start, "start");
        end = Objects.requireNonNull(end, "end");
        dimensions = Objects.requireNonNull(dimensions, "dimensions");
        validation = Objects.requireNonNull(validation, "validation");
    }

    public static SelectionPanelModel from(SelectionController.Snapshot snapshot) {
        Objects.requireNonNull(snapshot, "snapshot");
        Optional<Dimensions> dimensions = snapshot.selection().bounds().map(SelectionPanelModel::dimensionsOf);
        return new SelectionPanelModel(
                snapshot.revision(),
                snapshot.selection().start(),
                snapshot.selection().end(),
                dimensions,
                snapshot.validation());
    }

    private static Dimensions dimensionsOf(SelectionBounds bounds) {
        return new Dimensions(bounds.width(), bounds.height(), bounds.depth(), bounds.blockCount());
    }
}
