/*
 * SPDX-License-Identifier: MPL-2.0
 */
package dev.sakus.mcad.minecraft.selection;

import java.util.Objects;
import java.util.Optional;

/**
 * Immutable two-corner selection state.
 */
public record TwoPointSelection(Optional<SelectionPoint> start, Optional<SelectionPoint> end) {
    public enum Corner {
        START,
        END
    }

    public TwoPointSelection {
        start = Objects.requireNonNull(start, "start");
        end = Objects.requireNonNull(end, "end");
    }

    public static TwoPointSelection empty() {
        return new TwoPointSelection(Optional.empty(), Optional.empty());
    }

    public TwoPointSelection withStart(SelectionPoint point) {
        return new TwoPointSelection(Optional.of(Objects.requireNonNull(point, "point")), end);
    }

    public TwoPointSelection withEnd(SelectionPoint point) {
        return new TwoPointSelection(start, Optional.of(Objects.requireNonNull(point, "point")));
    }

    public TwoPointSelection withCorner(Corner corner, SelectionPoint point) {
        Objects.requireNonNull(corner, "corner");
        return switch (corner) {
            case START -> withStart(point);
            case END -> withEnd(point);
        };
    }

    public TwoPointSelection clearCorner(Corner corner) {
        Objects.requireNonNull(corner, "corner");
        return switch (corner) {
            case START -> new TwoPointSelection(Optional.empty(), end);
            case END -> new TwoPointSelection(start, Optional.empty());
        };
    }

    public TwoPointSelection clear() {
        return empty();
    }

    public Optional<SelectionBounds> bounds() {
        if (start.isEmpty() || end.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(SelectionBounds.between(start.orElseThrow(), end.orElseThrow()));
    }

    public SelectionValidation validate(long maximumBlockCount) {
        return bounds()
                .map(value -> SelectionValidation.validate(value, maximumBlockCount))
                .orElseGet(SelectionValidation::incomplete);
    }
}
