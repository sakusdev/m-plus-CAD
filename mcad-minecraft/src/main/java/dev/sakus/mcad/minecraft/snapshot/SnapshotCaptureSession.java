/*
 * SPDX-License-Identifier: MPL-2.0
 */
package dev.sakus.mcad.minecraft.snapshot;

import dev.sakus.mcad.api.ApiVersions;
import dev.sakus.mcad.api.BlockEntry;
import dev.sakus.mcad.api.BlockPosition;
import dev.sakus.mcad.api.CancellationToken;
import dev.sakus.mcad.api.CanonicalIdentifier;
import dev.sakus.mcad.api.IntSize3;
import dev.sakus.mcad.api.MetadataValue;
import dev.sakus.mcad.api.ProgressReporter;
import dev.sakus.mcad.api.ProgressUpdate;
import dev.sakus.mcad.api.StructureSnapshot;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.TreeMap;
import java.util.concurrent.CancellationException;

/**
 * Staged world capture. Each step must execute on the loader-approved world-read thread.
 * Partial block data is never exposed as a StructureSnapshot.
 */
public final class SnapshotCaptureSession {
    private static final CanonicalIdentifier OMITTED_AIR_COUNT =
            new CanonicalIdentifier("mcad", "omitted_air_cells");
    private static final CanonicalIdentifier AIR_OMITTED =
            new CanonicalIdentifier("mcad", "air_omitted");

    private final SnapshotWorldView world;
    private final SnapshotSelection selection;
    private final SnapshotLimits limits;
    private final SnapshotOptions options;
    private final ProgressReporter progressReporter;
    private final CancellationToken cancellationToken;
    private final IntSize3 size;
    private final long totalCells;
    private ArrayList<BlockEntry> retainedBlocks;
    private SnapshotCaptureStatus status = SnapshotCaptureStatus.RUNNING;
    private StructureSnapshot snapshot;
    private int relativeX;
    private int relativeY;
    private int relativeZ;
    private long completedCells;
    private long omittedAirCells;
    private long lastReportedCells = -1L;

    SnapshotCaptureSession(
            SnapshotWorldView world,
            SnapshotSelection selection,
            SnapshotLimits limits,
            SnapshotOptions options,
            ProgressReporter progressReporter,
            CancellationToken cancellationToken) {
        this.world = Objects.requireNonNull(world, "world");
        this.selection = Objects.requireNonNull(selection, "selection");
        this.limits = Objects.requireNonNull(limits, "limits");
        this.options = Objects.requireNonNull(options, "options");
        this.progressReporter = Objects.requireNonNull(progressReporter, "progressReporter");
        this.cancellationToken = Objects.requireNonNull(cancellationToken, "cancellationToken");
        limits.validate(selection);
        size = selection.size();
        totalCells = size.volume();
        retainedBlocks = new ArrayList<>((int) Math.min(totalCells, 4_096L));
    }

    public SnapshotCaptureStatus status() {
        return status;
    }

    public long completedCells() {
        return completedCells;
    }

    public long totalCells() {
        return totalCells;
    }

    public SnapshotCaptureStatus step(int maxCells) {
        if (maxCells <= 0) {
            throw new IllegalArgumentException("maxCells must be positive");
        }
        if (status == SnapshotCaptureStatus.COMPLETE) {
            return status;
        }
        if (status != SnapshotCaptureStatus.RUNNING) {
            throw new IllegalStateException("capture is not running: " + status);
        }

        try {
            world.assertReadThread();
            reportProgress(false);
            for (int processed = 0; processed < maxCells && completedCells < totalCells; processed++) {
                cancellationToken.throwIfCancellationRequested();
                captureCurrentCell();
                completedCells++;
                advance();
                reportProgress(false);
            }
            if (completedCells == totalCells) {
                complete();
            }
            return status;
        } catch (CancellationException exception) {
            discard(SnapshotCaptureStatus.CANCELLED);
            throw exception;
        } catch (RuntimeException exception) {
            discard(SnapshotCaptureStatus.FAILED);
            throw exception;
        }
    }

    public StructureSnapshot snapshot() {
        if (status != SnapshotCaptureStatus.COMPLETE || snapshot == null) {
            throw new IllegalStateException("snapshot is unavailable until capture completes");
        }
        return snapshot;
    }

    private void captureCurrentCell() {
        int worldX = Math.addExact(selection.minInclusive().x(), relativeX);
        int worldY = Math.addExact(selection.minInclusive().y(), relativeY);
        int worldZ = Math.addExact(selection.minInclusive().z(), relativeZ);
        var worldPosition = new BlockPosition(worldX, worldY, worldZ);
        if (!world.isLoaded(worldX, worldY, worldZ)) {
            throw new UnloadedSelectionException(worldPosition);
        }

        SnapshotBlockData data = Objects.requireNonNull(
                world.readBlock(worldX, worldY, worldZ), "world returned null block data");
        if (data.isAir()) {
            omittedAirCells++;
            return;
        }
        if (retainedBlocks.size() >= limits.maxRetainedBlocks()) {
            throw new SnapshotLimitException(
                    "retained block count exceeds limit: " + limits.maxRetainedBlocks());
        }

        var blockId = SnapshotCanonicalizer.blockIdentifier(data.blockIdentifier().orElseThrow());
        var stateProperties = SnapshotCanonicalizer.stateProperties(data.stateProperties());
        retainedBlocks.add(new BlockEntry(
                new BlockPosition(relativeX, relativeY, relativeZ),
                blockId,
                stateProperties,
                data.metadata()));
    }

    private void advance() {
        relativeX++;
        if (relativeX == size.width()) {
            relativeX = 0;
            relativeZ++;
            if (relativeZ == size.depth()) {
                relativeZ = 0;
                relativeY++;
            }
        }
    }

    private void complete() {
        var metadata = new TreeMap<CanonicalIdentifier, MetadataValue>(options.metadata());
        metadata.put(AIR_OMITTED, new MetadataValue.BooleanValue(true));
        if (options.recordOmittedAirCount()) {
            metadata.put(OMITTED_AIR_COUNT, new MetadataValue.LongValue(omittedAirCells));
        }
        Optional<BlockPosition> origin = options.includeSourceWorldOrigin()
                ? Optional.of(selection.minInclusive())
                : Optional.empty();
        List<BlockEntry> blocks = List.copyOf(retainedBlocks);
        String snapshotId = SnapshotContentHasher.hash(
                ApiVersions.STRUCTURE_SNAPSHOT,
                size,
                origin,
                blocks,
                metadata);
        snapshot = new StructureSnapshot(
                ApiVersions.STRUCTURE_SNAPSHOT,
                snapshotId,
                size,
                origin,
                blocks,
                metadata);
        retainedBlocks.clear();
        retainedBlocks = null;
        status = SnapshotCaptureStatus.COMPLETE;
        reportProgress(true);
    }

    private void reportProgress(boolean force) {
        if (!force
                && lastReportedCells >= 0
                && completedCells - lastReportedCells < limits.progressInterval()
                && completedCells != totalCells) {
            return;
        }
        progressReporter.report(new ProgressUpdate(
                "snapshot-read",
                completedCells,
                OptionalLong.of(totalCells),
                completedCells == totalCells ? "snapshot complete" : "reading selected world cells"));
        lastReportedCells = completedCells;
    }

    private void discard(SnapshotCaptureStatus terminalStatus) {
        if (retainedBlocks != null) {
            retainedBlocks.clear();
            retainedBlocks = null;
        }
        snapshot = null;
        status = terminalStatus;
    }
}
