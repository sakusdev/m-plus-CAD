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
        relativeBlockPosition.ifPresent(position -> {
            if (position.x() < 0 || position.y() < 0 || position.z() < 0) {
                throw new IllegalArgumentException("relativeBlockPosition must be non-negative");
            }
        });
        blockId = Checks.notNull(blockId, "blockId");
        markerRuleId = Checks.notNull(markerRuleId, "markerRuleId");
    }

    String stableSortKey() {
        String positionKey = relativeBlockPosition
                .map(position -> position.x() + "," + position.y() + "," + position.z())
                .orElse("");
        return snapshotId + "|" + positionKey
                + "|" + blockId.map(Object::toString).orElse("")
                + "|" + markerRuleId.map(Object::toString).orElse("");
    }
}
