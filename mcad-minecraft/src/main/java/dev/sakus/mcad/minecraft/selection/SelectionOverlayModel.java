/*
 * SPDX-License-Identifier: MPL-2.0
 */
package dev.sakus.mcad.minecraft.selection;

import java.math.BigInteger;
import java.util.Objects;
import java.util.Optional;

/**
 * Complete renderer-neutral state for the in-world selection overlay.
 *
 * <p>The Minecraft rendering adapter only needs to draw {@link #wireframe()} and text labels. It
 * never computes bounds or reads a live world.</p>
 */
public record SelectionOverlayModel(
        long revision,
        Optional<SelectionWireframe> wireframe,
        String dimensionsLabel,
        String blockCountLabel,
        SelectionValidation.Code validationCode,
        String message,
        String suggestedAction) {

    public SelectionOverlayModel {
        if (revision < 0L) {
            throw new IllegalArgumentException("revision must be non-negative");
        }
        wireframe = Objects.requireNonNull(wireframe, "wireframe");
        dimensionsLabel = Objects.requireNonNull(dimensionsLabel, "dimensionsLabel");
        blockCountLabel = Objects.requireNonNull(blockCountLabel, "blockCountLabel");
        validationCode = Objects.requireNonNull(validationCode, "validationCode");
        message = Objects.requireNonNull(message, "message");
        suggestedAction = Objects.requireNonNull(suggestedAction, "suggestedAction");
        if (validationCode == SelectionValidation.Code.INCOMPLETE && wireframe.isPresent()) {
            throw new IllegalArgumentException("incomplete selection must not expose a wireframe");
        }
        if (validationCode != SelectionValidation.Code.INCOMPLETE && wireframe.isEmpty()) {
            throw new IllegalArgumentException("complete selection must expose a wireframe");
        }
    }

    public static SelectionOverlayModel from(SelectionController.Snapshot snapshot) {
        Objects.requireNonNull(snapshot, "snapshot");
        SelectionValidation validation = snapshot.validation();
        Optional<SelectionBounds> bounds = validation.bounds();
        if (bounds.isEmpty()) {
            return new SelectionOverlayModel(
                    snapshot.revision(),
                    Optional.empty(),
                    "— × — × —",
                    "—",
                    validation.code(),
                    validation.message(),
                    validation.suggestedAction());
        }

        SelectionBounds selected = bounds.orElseThrow();
        BigInteger blockCount = selected.blockCount();
        return new SelectionOverlayModel(
                snapshot.revision(),
                Optional.of(SelectionWireframe.from(selected)),
                selected.width() + " × " + selected.height() + " × " + selected.depth(),
                blockCount.toString(),
                validation.code(),
                validation.message(),
                validation.suggestedAction());
    }

    public boolean valid() {
        return validationCode == SelectionValidation.Code.VALID;
    }
}
