/*
 * SPDX-License-Identifier: MPL-2.0
 */
package dev.sakus.mcad.minecraft.selection;

import java.util.Objects;
import java.util.Optional;

/**
 * User-action boundary between Minecraft input hooks and the neutral selection state.
 *
 * <p>The platform adapter performs targeting on the client thread and passes only an optional
 * primitive block position here.</p>
 */
public final class SelectionInteractionController {
    public enum Action {
        SET_START,
        SET_END,
        CLEAR_START,
        CLEAR_END,
        CLEAR_ALL
    }

    public enum Status {
        APPLIED,
        NO_TARGET
    }

    public record Result(Status status, SelectionController.Snapshot snapshot, String message) {
        public Result {
            Objects.requireNonNull(status, "status");
            Objects.requireNonNull(snapshot, "snapshot");
            Objects.requireNonNull(message, "message");
            if (message.isBlank()) {
                throw new IllegalArgumentException("message must not be blank");
            }
        }
    }

    private final SelectionController selectionController;

    public SelectionInteractionController(SelectionController selectionController) {
        this.selectionController = Objects.requireNonNull(selectionController, "selectionController");
    }

    public Result apply(Action action, Optional<SelectionPoint> targetedBlock) {
        Objects.requireNonNull(action, "action");
        targetedBlock = Objects.requireNonNull(targetedBlock, "targetedBlock");
        return switch (action) {
            case SET_START -> setCorner(
                    TwoPointSelection.Corner.START,
                    targetedBlock,
                    "始点を更新しました。",
                    "始点を設定するブロックを見てください。");
            case SET_END -> setCorner(
                    TwoPointSelection.Corner.END,
                    targetedBlock,
                    "終点を更新しました。",
                    "終点を設定するブロックを見てください。");
            case CLEAR_START -> new Result(
                    Status.APPLIED,
                    selectionController.clearCorner(TwoPointSelection.Corner.START),
                    "始点を解除しました。");
            case CLEAR_END -> new Result(
                    Status.APPLIED,
                    selectionController.clearCorner(TwoPointSelection.Corner.END),
                    "終点を解除しました。");
            case CLEAR_ALL -> new Result(
                    Status.APPLIED,
                    selectionController.clear(),
                    "選択範囲を解除しました。");
        };
    }

    private Result setCorner(
            TwoPointSelection.Corner corner,
            Optional<SelectionPoint> targetedBlock,
            String appliedMessage,
            String noTargetMessage) {
        if (targetedBlock.isEmpty()) {
            return new Result(Status.NO_TARGET, selectionController.snapshot(), noTargetMessage);
        }
        return new Result(
                Status.APPLIED,
                selectionController.setCorner(corner, targetedBlock.orElseThrow()),
                appliedMessage);
    }
}
