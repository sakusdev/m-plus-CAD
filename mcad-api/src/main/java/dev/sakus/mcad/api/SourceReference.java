/*
 * SPDX-License-Identifier: MPL-2.0
 */
package dev.sakus.mcad.api;


import java.util.Optional;

public record SourceReference(
        String snapshotId,
        Optional<BlockPosition> relativeBlockPosition,
        Optional<CanonicalIdentifier> blockId,
        Optional<CanonicalIdentifier> markerRuleId) {

    public SourceReference {
        Checks.stableId(snapshotId, "snapshotId");
        relativeBlockPosition = Checks.notNull(relativeBlockPosition, "relativeBlockPosition");
        blockId = Checks.notNull(blockId, "blockId");
        markerRuleId = Checks.notNull(markerRuleId, "markerRuleId");
    }

    String stableSortKey() {
        return snapshotId + "|" + relativeBlockPosition.map(Object::toString).orElse("")
                + "|" + blockId.map(Object::toString).orElse("")
                + "|" + markerRuleId.map(Object::toString).orElse("");
    }
}
