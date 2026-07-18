/*
 * SPDX-License-Identifier: MPL-2.0
 */
package dev.sakus.mcad.minecraft.snapshot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.sakus.mcad.api.BlockPosition;
import dev.sakus.mcad.api.CancellationToken;
import dev.sakus.mcad.api.CanonicalIdentifier;
import dev.sakus.mcad.api.MetadataValue;
import dev.sakus.mcad.api.ProgressUpdate;
import dev.sakus.mcad.api.StructureSnapshot;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

final class WorldSnapshotAdapterTest {
    private static final CanonicalIdentifier OMITTED_AIR_COUNT =
            new CanonicalIdentifier("mcad", "omitted_air_cells");

    @Test
    void capturesSingleBlockAtRelativeOrigin() {
        var world = new FakeWorld();
        world.put(4, 5, 6, block("minecraft:stone", Map.of()));

        StructureSnapshot snapshot = capture(world, selection(4, 5, 6, 5, 6, 7));

        assertEquals(1, snapshot.blocks().size());
        assertEquals(new BlockPosition(0, 0, 0), snapshot.blocks().getFirst().relativePosition());
        assertEquals("minecraft:stone", snapshot.blocks().getFirst().blockId().toString());
        assertEquals(1, snapshot.size().width());
        assertEquals(1, snapshot.size().height());
        assertEquals(1, snapshot.size().depth());
    }

    @Test
    void capturesAdjacentMixedBlocksWithoutRegrouping() {
        var world = new FakeWorld();
        world.put(0, 0, 0, block("minecraft:stone", Map.of()));
        world.put(1, 0, 0, block("minecraft:dirt", Map.of()));

        StructureSnapshot snapshot = capture(world, selection(0, 0, 0, 2, 1, 1));

        assertEquals(List.of("minecraft:stone", "minecraft:dirt"), snapshot.blocks().stream()
                .map(entry -> entry.blockId().toString())
                .toList());
        assertEquals(List.of(new BlockPosition(0, 0, 0), new BlockPosition(1, 0, 0)), snapshot.blocks().stream()
                .map(entry -> entry.relativePosition())
                .toList());
    }

    @Test
    void convertsNegativeWorldCoordinatesToRelativeCoordinates() {
        var world = new FakeWorld();
        world.put(-2, -4, -6, block("minecraft:gold_block", Map.of()));

        StructureSnapshot snapshot = capture(world, selection(-2, -4, -6, -1, -3, -5));

        assertEquals(new BlockPosition(-2, -4, -6), snapshot.sourceWorldOrigin().orElseThrow());
        assertEquals(new BlockPosition(0, 0, 0), snapshot.blocks().getFirst().relativePosition());
    }

    @Test
    void canonicalizesBlockIdAndStatePropertyOrder() {
        var world = new FakeWorld();
        var properties = new LinkedHashMap<String, String>();
        properties.put("WATERLOGGED", " TRUE ");
        properties.put("FACING", " NORTH ");
        world.put(0, 0, 0, block(" MINECRAFT:OAK_STAIRS ", properties));

        var block = capture(world, selection(0, 0, 0, 1, 1, 1)).blocks().getFirst();

        assertEquals("minecraft:oak_stairs", block.blockId().toString());
        assertEquals(List.of("facing", "waterlogged"), new ArrayList<>(block.stateProperties().keySet()));
        assertEquals("north", block.stateProperties().get("facing"));
        assertThrows(UnsupportedOperationException.class, () -> block.stateProperties().put("x", "y"));
    }

    @Test
    void iteratesBlocksInLexicographicYZXOrder() {
        var world = new FakeWorld();
        world.put(1, 0, 1, block("example:a", Map.of()));
        world.put(0, 1, 0, block("example:b", Map.of()));
        world.put(0, 0, 1, block("example:c", Map.of()));

        StructureSnapshot snapshot = capture(world, selection(0, 0, 0, 2, 2, 2));

        assertEquals(
                List.of(
                        new BlockPosition(0, 0, 1),
                        new BlockPosition(1, 0, 1),
                        new BlockPosition(0, 1, 0)),
                snapshot.blocks().stream().map(entry -> entry.relativePosition()).toList());
    }

    @Test
    void omitsAirAndRecordsNeutralMetadata() {
        var world = new FakeWorld();
        world.put(1, 0, 0, block("minecraft:stone", Map.of()));

        StructureSnapshot snapshot = capture(world, selection(0, 0, 0, 3, 1, 1));

        assertEquals(1, snapshot.blocks().size());
        assertEquals(2L, ((MetadataValue.LongValue) snapshot.metadata().get(OMITTED_AIR_COUNT)).value());
        assertTrue(((MetadataValue.BooleanValue) snapshot.metadata()
                .get(new CanonicalIdentifier("mcad", "air_omitted"))).value());
    }

    @Test
    void cancellationDiscardsPartialSnapshot() {
        var world = new FakeWorld();
        for (int x = 0; x < 10; x++) {
            world.put(x, 0, 0, block("minecraft:stone", Map.of()));
        }
        var checks = new AtomicInteger();
        CancellationToken token = () -> checks.incrementAndGet() > 3;
        SnapshotCaptureSession session = new WorldSnapshotAdapter().beginCapture(
                world,
                selection(0, 0, 0, 10, 1, 1),
                SnapshotLimits.defaults(),
                SnapshotOptions.defaults(),
                update -> { },
                token);

        assertThrows(CancellationException.class, () -> session.step(10));

        assertEquals(SnapshotCaptureStatus.CANCELLED, session.status());
        assertThrows(IllegalStateException.class, session::snapshot);
        assertThrows(IllegalStateException.class, () -> session.step(1));
    }

    @Test
    void rejectsInvalidOversizedAndOverRetainedSelections() {
        var limits = new SnapshotLimits(2, 2, 2, 8, 1, 1);
        assertThrows(SnapshotLimitException.class, () -> new WorldSnapshotAdapter().beginCapture(
                new FakeWorld(),
                selection(0, 0, 0, 3, 1, 1),
                limits,
                SnapshotOptions.defaults(),
                update -> { },
                CancellationToken.NONE));
        assertThrows(IllegalArgumentException.class, () -> selection(0, 0, 0, 0, 1, 1));
        assertThrows(IllegalArgumentException.class, () -> new SnapshotSelection(
                new BlockPosition(Integer.MIN_VALUE, 0, 0),
                new BlockPosition(Integer.MAX_VALUE, 1, 1)));

        var world = new FakeWorld();
        world.put(0, 0, 0, block("minecraft:stone", Map.of()));
        world.put(1, 0, 0, block("minecraft:dirt", Map.of()));
        SnapshotCaptureSession retainedLimit = new WorldSnapshotAdapter().beginCapture(
                world,
                selection(0, 0, 0, 2, 1, 1),
                limits,
                SnapshotOptions.defaults(),
                update -> { },
                CancellationToken.NONE);
        assertThrows(SnapshotLimitException.class, () -> retainedLimit.step(2));
        assertEquals(SnapshotCaptureStatus.FAILED, retainedLimit.status());
    }

    @Test
    void rejectsUnloadedCellsInsteadOfSilentlyDroppingThem() {
        var world = new FakeWorld();
        world.unloaded(1, 0, 0);
        SnapshotCaptureSession session = new WorldSnapshotAdapter().beginCapture(
                world,
                selection(0, 0, 0, 2, 1, 1),
                SnapshotLimits.defaults(),
                SnapshotOptions.defaults(),
                update -> { },
                CancellationToken.NONE);

        UnloadedSelectionException exception = assertThrows(UnloadedSelectionException.class, () -> session.step(2));

        assertEquals(new BlockPosition(1, 0, 0), exception.worldPosition());
        assertEquals(SnapshotCaptureStatus.FAILED, session.status());
        assertThrows(IllegalStateException.class, session::snapshot);
    }

    @Test
    void stagedCaptureReportsProgressAndChecksThreadEveryStep() {
        var world = new FakeWorld();
        world.put(0, 0, 0, block("minecraft:stone", Map.of()));
        world.put(1, 0, 0, block("minecraft:dirt", Map.of()));
        var progress = new ArrayList<ProgressUpdate>();
        SnapshotCaptureSession session = new WorldSnapshotAdapter().beginCapture(
                world,
                selection(0, 0, 0, 2, 1, 1),
                new SnapshotLimits(10, 10, 10, 100, 100, 1),
                SnapshotOptions.defaults(),
                progress::add,
                CancellationToken.NONE);

        assertEquals(SnapshotCaptureStatus.RUNNING, session.step(1));
        assertEquals(1, session.completedCells());
        assertThrows(IllegalStateException.class, session::snapshot);
        assertEquals(SnapshotCaptureStatus.COMPLETE, session.step(1));

        assertEquals(2, progress.getLast().completed());
        assertEquals(2, progress.getLast().total().orElseThrow());
        assertEquals(2, world.assertThreadCalls);
    }

    @Test
    void identicalLogicalInputHasIdenticalIdentity() {
        var first = new FakeWorld();
        var second = new FakeWorld();
        var firstProperties = new LinkedHashMap<String, String>();
        firstProperties.put("axis", "x");
        firstProperties.put("powered", "false");
        var secondProperties = new LinkedHashMap<String, String>();
        secondProperties.put("powered", " FALSE ");
        secondProperties.put("AXIS", " X ");
        first.put(2, 3, 4, block("minecraft:oak_log", firstProperties));
        second.put(2, 3, 4, block("MINECRAFT:OAK_LOG", secondProperties));

        StructureSnapshot left = capture(first, selection(2, 3, 4, 4, 4, 5));
        StructureSnapshot right = capture(second, selection(2, 3, 4, 4, 4, 5));

        assertEquals(left.snapshotId(), right.snapshotId());
        assertEquals(left.blocks(), right.blocks());
    }

    @Test
    void fixtureIdentityIsStable() throws IOException {
        StructureSnapshot snapshot = deterministicFixtureSnapshot();
        String expectedId = Files.readAllLines(fixturePath()).stream()
                .filter(line -> line.startsWith("expectedSnapshotId="))
                .map(line -> line.substring("expectedSnapshotId=".length()))
                .findFirst()
                .orElseThrow();

        assertEquals(expectedId, snapshot.snapshotId());
        assertEquals(2, snapshot.blocks().size());
        assertEquals(new BlockPosition(-2, 10, 5), snapshot.sourceWorldOrigin().orElseThrow());
    }

    @Test
    void validatesMetadataBoundsAndReservedKeys() {
        CanonicalIdentifier reserved = new CanonicalIdentifier("mcad", "air_omitted");
        assertThrows(IllegalArgumentException.class, () -> new SnapshotOptions(
                true,
                true,
                Map.of(reserved, new MetadataValue.BooleanValue(false))));

        MetadataValue nested = new MetadataValue.StringValue("leaf");
        for (int depth = 0; depth < 17; depth++) {
            nested = new MetadataValue.ListValue(List.of(nested));
        }
        MetadataValue tooDeep = nested;
        assertThrows(IllegalArgumentException.class, () -> SnapshotBlockData.block(
                "example:block",
                Map.of(),
                Map.of(new CanonicalIdentifier("example", "nested"), tooDeep)));
    }

    @Test
    void inputCollectionsAreDefensivelyCopied() {
        var properties = new LinkedHashMap<String, String>();
        properties.put("axis", "x");
        var metadata = new HashMap<CanonicalIdentifier, MetadataValue>();
        metadata.put(new CanonicalIdentifier("example", "value"), new MetadataValue.LongValue(1));
        SnapshotBlockData data = SnapshotBlockData.block("example:block", properties, metadata);

        properties.put("changed", "true");
        metadata.clear();

        assertFalse(data.stateProperties().containsKey("changed"));
        assertEquals(1, data.metadata().size());
        assertThrows(UnsupportedOperationException.class, () -> data.metadata().clear());
    }

    private static StructureSnapshot deterministicFixtureSnapshot() {
        var world = new FakeWorld();
        var firstProperties = new LinkedHashMap<String, String>();
        firstProperties.put("POWERED", " FALSE ");
        firstProperties.put("AXIS", " Z ");
        world.put(-2, 10, 5, SnapshotBlockData.block(
                " MINECRAFT:OAK_LOG ",
                firstProperties,
                Map.of(new CanonicalIdentifier("mcad", "block_light"), new MetadataValue.LongValue(7))));
        var secondProperties = new LinkedHashMap<String, String>();
        secondProperties.put("beta", "2");
        secondProperties.put("alpha", "1");
        world.put(0, 11, 6, SnapshotBlockData.block("example:custom_block", secondProperties, Map.of()));
        var options = new SnapshotOptions(
                true,
                true,
                Map.of(new CanonicalIdentifier("example", "fixture"),
                        new MetadataValue.StringValue("deterministic-basic")));
        return new WorldSnapshotAdapter().capture(
                world,
                selection(-2, 10, 5, 1, 12, 7),
                SnapshotLimits.defaults(),
                options,
                update -> { },
                CancellationToken.NONE,
                3);
    }

    private static Path fixturePath() {
        Path rootRelative = Path.of("fixtures", "snapshots", "deterministic-basic.snapshot");
        if (Files.isRegularFile(rootRelative)) {
            return rootRelative;
        }
        Path moduleRelative = Path.of("..", "fixtures", "snapshots", "deterministic-basic.snapshot");
        if (Files.isRegularFile(moduleRelative)) {
            return moduleRelative;
        }
        throw new IllegalStateException("snapshot fixture not found");
    }

    private static StructureSnapshot capture(FakeWorld world, SnapshotSelection selection) {
        return new WorldSnapshotAdapter().capture(
                world,
                selection,
                SnapshotLimits.defaults(),
                SnapshotOptions.defaults(),
                update -> { },
                CancellationToken.NONE,
                2);
    }

    private static SnapshotSelection selection(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        return new SnapshotSelection(new BlockPosition(minX, minY, minZ), new BlockPosition(maxX, maxY, maxZ));
    }

    private static SnapshotBlockData block(String id, Map<String, String> properties) {
        return SnapshotBlockData.block(id, properties, Map.of());
    }

    private static final class FakeWorld implements SnapshotWorldView {
        private final Map<BlockPosition, SnapshotBlockData> blocks = new HashMap<>();
        private final Map<BlockPosition, Boolean> unloaded = new HashMap<>();
        private int assertThreadCalls;

        void put(int x, int y, int z, SnapshotBlockData block) {
            blocks.put(new BlockPosition(x, y, z), block);
        }

        void unloaded(int x, int y, int z) {
            unloaded.put(new BlockPosition(x, y, z), Boolean.TRUE);
        }

        @Override
        public void assertReadThread() {
            assertThreadCalls++;
        }

        @Override
        public boolean isLoaded(int worldX, int worldY, int worldZ) {
            return !unloaded.containsKey(new BlockPosition(worldX, worldY, worldZ));
        }

        @Override
        public SnapshotBlockData readBlock(int worldX, int worldY, int worldZ) {
            return blocks.getOrDefault(new BlockPosition(worldX, worldY, worldZ), SnapshotBlockData.air());
        }
    }
}
