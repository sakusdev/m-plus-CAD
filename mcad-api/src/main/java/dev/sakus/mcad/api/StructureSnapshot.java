/*
 * SPDX-License-Identifier: MPL-2.0
 */
package dev.sakus.mcad.api;


import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Optional;

public record StructureSnapshot(
        SchemaVersion schemaVersion,
        String snapshotId,
        IntSize3 size,
        Optional<BlockPosition> sourceWorldOrigin,
        List<BlockEntry> blocks,
        NavigableMap<CanonicalIdentifier, MetadataValue> metadata) {

    public StructureSnapshot(
            SchemaVersion schemaVersion,
            String snapshotId,
            IntSize3 size,
            Optional<BlockPosition> sourceWorldOrigin,
            List<BlockEntry> blocks,
            Map<CanonicalIdentifier, MetadataValue> metadata) {
        this(
                schemaVersion,
                snapshotId,
                size,
                sourceWorldOrigin,
                blocks,
                Checks.immutableSortedMap(metadata, CanonicalIdentifier::compareTo, "metadata"));
    }

    public StructureSnapshot {
        Checks.notNull(schemaVersion, "schemaVersion");
        Checks.stableId(snapshotId, "snapshotId");
        Checks.notNull(size, "size");
        sourceWorldOrigin = Checks.notNull(sourceWorldOrigin, "sourceWorldOrigin");
        metadata = Checks.immutableSortedMap(metadata, CanonicalIdentifier::compareTo, "metadata");

        var copy = new ArrayList<BlockEntry>(Checks.notNull(blocks, "blocks"));
        copy.sort((left, right) -> left.relativePosition().compareTo(right.relativePosition()));
        var positions = new HashSet<BlockPosition>();
        for (BlockEntry block : copy) {
            Checks.notNull(block, "block");
            if (!size.contains(block.relativePosition())) {
                throw new IllegalArgumentException("block position is outside snapshot bounds: " + block.relativePosition());
            }
            if (!positions.add(block.relativePosition())) {
                throw new IllegalArgumentException("duplicate block position: " + block.relativePosition());
            }
        }
        blocks = List.copyOf(copy);
    }
}
