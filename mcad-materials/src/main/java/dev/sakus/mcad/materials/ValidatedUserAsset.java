/*
 * SPDX-License-Identifier: MPL-2.0
 */
package dev.sakus.mcad.materials;

import dev.sakus.mcad.api.CanonicalIdentifier;
import dev.sakus.mcad.api.UserAssetReference;
import java.nio.file.Path;
import java.util.Objects;

/** Existing user asset verified to remain inside the selected project root. */
public record ValidatedUserAsset(
        CanonicalIdentifier blockId,
        String materialStableId,
        UserAssetReference reference,
        Path resolvedPath) implements Comparable<ValidatedUserAsset> {

    public ValidatedUserAsset {
        Objects.requireNonNull(blockId, "blockId");
        Objects.requireNonNull(materialStableId, "materialStableId");
        Objects.requireNonNull(reference, "reference");
        resolvedPath = Objects.requireNonNull(resolvedPath, "resolvedPath").toAbsolutePath().normalize();
    }

    public boolean copyRequested() {
        return reference.copyOnExport();
    }

    @Override
    public int compareTo(ValidatedUserAsset other) {
        int blockComparison = blockId.compareTo(other.blockId);
        if (blockComparison != 0) {
            return blockComparison;
        }
        int materialComparison = materialStableId.compareTo(other.materialStableId);
        return materialComparison != 0 ? materialComparison : reference.compareTo(other.reference);
    }
}
