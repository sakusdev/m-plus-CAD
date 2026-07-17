/*
 * SPDX-License-Identifier: MPL-2.0
 */
package dev.sakus.mcad.core.mesh;

import dev.sakus.mcad.api.ApiVersions;
import dev.sakus.mcad.api.BlockEntry;
import dev.sakus.mcad.api.BlockPosition;
import dev.sakus.mcad.api.CancellationToken;
import dev.sakus.mcad.api.CanonicalIdentifier;
import dev.sakus.mcad.api.Diagnostic;
import dev.sakus.mcad.api.DiagnosticSeverity;
import dev.sakus.mcad.api.GeneratedScene;
import dev.sakus.mcad.api.MeshGroup;
import dev.sakus.mcad.api.MeshPrimitive;
import dev.sakus.mcad.api.ProgressReporter;
import dev.sakus.mcad.api.ProgressUpdate;
import dev.sakus.mcad.api.ProjectSettings;
import dev.sakus.mcad.api.SceneNode;
import dev.sakus.mcad.api.SceneStatistics;
import dev.sakus.mcad.api.StructureSnapshot;
import dev.sakus.mcad.api.Transform;
import dev.sakus.mcad.api.Vec3d;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.TreeMap;
import java.util.concurrent.CancellationException;

/**
 * Deterministic MVP mesh generator for opaque full-cube blocks.
 *
 * <p>The generator consumes only neutral API values. It removes both faces of every occupied
 * block-to-block boundary when hidden-face removal is enabled, regardless of block identifier,
 * while preserving a distinct mesh group for every canonical block identifier that has visible
 * geometry.</p>
 */
public final class FullCubeMeshGenerator {
    private static final int CANCELLATION_CHECK_INTERVAL = 64;
    private static final int PROGRESS_REPORT_INTERVAL = 256;
    private static final CanonicalIdentifier COMPLETE_CODE =
            new CanonicalIdentifier("mcad", "full_cube_mesh_generated");
    private static final CanonicalIdentifier UNSUPPORTED_SEPARATION_CODE =
            new CanonicalIdentifier("mcad", "unsupported_mesh_separation_key");
    private static final CanonicalIdentifier GREEDY_DISABLED_CODE =
            new CanonicalIdentifier("mcad", "greedy_meshing_not_supported");
    private static final CanonicalIdentifier EMPTY_GROUP_CODE =
            new CanonicalIdentifier("mcad", "empty_mesh_group_omitted");

    /**
     * Generates a neutral scene from a detached structure snapshot.
     *
     * @throws IllegalArgumentException when the input exceeds configured or representable limits
     * @throws CancellationException when cancellation is requested
     */
    public GeneratedScene generate(
            StructureSnapshot snapshot,
            ProjectSettings settings,
            CancellationToken cancellationToken,
            ProgressReporter progressReporter) {
        Objects.requireNonNull(snapshot, "snapshot");
        Objects.requireNonNull(settings, "settings");
        Objects.requireNonNull(cancellationToken, "cancellationToken");
        Objects.requireNonNull(progressReporter, "progressReporter");

        validateInputSize(snapshot, settings);
        cancellationToken.throwIfCancellationRequested();

        List<Diagnostic> diagnostics = new ArrayList<>();
        collectSettingDiagnostics(settings, diagnostics);

        Map<BlockPosition, BlockEntry> occupied = new HashMap<>(mapCapacity(snapshot.blocks().size()));
        NavigableMap<CanonicalIdentifier, MeshAccumulator> accumulators = new TreeMap<>();
        int indexed = 0;
        for (BlockEntry block : snapshot.blocks()) {
            if ((indexed % CANCELLATION_CHECK_INTERVAL) == 0) {
                cancellationToken.throwIfCancellationRequested();
            }
            occupied.put(block.relativePosition(), block);
            accumulators.computeIfAbsent(block.blockId(), ignored -> new MeshAccumulator());
            indexed++;
        }

        long totalBlocks = snapshot.blocks().size();
        report(progressReporter, 0, totalBlocks, "Generating full-cube geometry");

        long visibleFaces = 0;
        long removedFaces = 0;
        int processed = 0;
        boolean hiddenFaceRemoval = settings.geometry().hiddenFaceRemoval();

        for (BlockEntry block : snapshot.blocks()) {
            if ((processed % CANCELLATION_CHECK_INTERVAL) == 0) {
                cancellationToken.throwIfCancellationRequested();
            }

            MeshAccumulator accumulator = accumulators.get(block.blockId());
            for (Face face : Face.values()) {
                BlockPosition neighbour = face.neighbour(block.relativePosition());
                if (hiddenFaceRemoval && occupied.containsKey(neighbour)) {
                    removedFaces++;
                    continue;
                }
                accumulator.addFace(block.relativePosition(), face);
                visibleFaces++;
            }

            processed++;
            if ((processed % PROGRESS_REPORT_INTERVAL) == 0 || processed == snapshot.blocks().size()) {
                report(progressReporter, processed, totalBlocks, "Generated geometry for " + processed + " blocks");
            }
        }

        cancellationToken.throwIfCancellationRequested();

        List<MeshGroup> meshes = new ArrayList<>();
        int completedGroups = 0;
        for (Map.Entry<CanonicalIdentifier, MeshAccumulator> entry : accumulators.entrySet()) {
            if ((completedGroups % CANCELLATION_CHECK_INTERVAL) == 0) {
                cancellationToken.throwIfCancellationRequested();
            }
            if (entry.getValue().isEmpty()) {
                diagnostics.add(new Diagnostic(
                        DiagnosticSeverity.INFO,
                        EMPTY_GROUP_CODE,
                        "Omitted block group with no visible faces: " + entry.getKey(),
                        Optional.empty(),
                        Map.of()));
                completedGroups++;
                continue;
            }
            meshes.add(entry.getValue().toMeshGroup(entry.getKey()));
            completedGroups++;
        }

        cancellationToken.throwIfCancellationRequested();

        long vertexCount = Math.multiplyExact(visibleFaces, 4L);
        long triangleCount = Math.multiplyExact(visibleFaces, 2L);
        SceneStatistics statistics = new SceneStatistics(
                snapshot.blocks().size(),
                visibleFaces,
                removedFaces,
                vertexCount,
                triangleCount,
                meshes.size(),
                0);

        diagnostics.add(new Diagnostic(
                DiagnosticSeverity.INFO,
                COMPLETE_CODE,
                "Generated " + visibleFaces + " visible full-cube faces in " + meshes.size() + " mesh groups",
                Optional.empty(),
                Map.of()));

        List<String> meshIds = meshes.stream().map(MeshGroup::stableId).toList();
        SceneNode root = new SceneNode(
                "node/root",
                "m+CAD generated structure",
                Optional.empty(),
                Transform.IDENTITY,
                meshIds,
                List.of(),
                Map.of(),
                List.of());

        return new GeneratedScene(
                ApiVersions.GENERATED_SCENE,
                "scene/" + snapshot.snapshotId() + "/full-cube-v1",
                List.of(root.stableId()),
                List.of(root),
                meshes,
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                Map.of(),
                diagnostics,
                statistics);
    }

    private static void validateInputSize(StructureSnapshot snapshot, ProjectSettings settings) {
        long blockCount = snapshot.blocks().size();
        if (blockCount > settings.selection().maximumBlockCount()) {
            throw new IllegalArgumentException(
                    "snapshot contains " + blockCount + " blocks, exceeding configured maximum "
                            + settings.selection().maximumBlockCount());
        }
        long maximumVertices = Math.multiplyExact(blockCount, 24L);
        if (maximumVertices > Integer.MAX_VALUE) {
            throw new IllegalArgumentException(
                    "snapshot may require more vertices than one indexed mesh can represent: " + maximumVertices);
        }
    }

    private static void collectSettingDiagnostics(ProjectSettings settings, List<Diagnostic> diagnostics) {
        EnumSet<ProjectSettings.SeparationKey> requested = settings.meshSeparation().orderedKeys().isEmpty()
                ? EnumSet.noneOf(ProjectSettings.SeparationKey.class)
                : EnumSet.copyOf(settings.meshSeparation().orderedKeys());

        if (!requested.contains(ProjectSettings.SeparationKey.BLOCK_IDENTIFIER)) {
            diagnostics.add(new Diagnostic(
                    DiagnosticSeverity.WARNING,
                    UNSUPPORTED_SEPARATION_CODE,
                    "The full-cube MVP always separates meshes by BLOCK_IDENTIFIER",
                    Optional.empty(),
                    Map.of()));
        }
        for (ProjectSettings.SeparationKey key : settings.meshSeparation().orderedKeys()) {
            if (key != ProjectSettings.SeparationKey.BLOCK_IDENTIFIER) {
                diagnostics.add(new Diagnostic(
                        DiagnosticSeverity.WARNING,
                        UNSUPPORTED_SEPARATION_CODE,
                        "The full-cube MVP does not yet apply separation key: " + key,
                        Optional.empty(),
                        Map.of()));
            }
        }
        if (settings.optimization().greedyMeshing()) {
            diagnostics.add(new Diagnostic(
                    DiagnosticSeverity.WARNING,
                    GREEDY_DISABLED_CODE,
                    "Greedy meshing is outside the full-cube MVP and was not applied",
                    Optional.empty(),
                    Map.of()));
        }
    }

    private static void report(
            ProgressReporter reporter,
            long completed,
            long total,
            String message) {
        reporter.report(new ProgressUpdate(
                "mesh.full_cube",
                completed,
                OptionalLong.of(total),
                message));
    }

    private static int mapCapacity(int size) {
        if (size < 3) {
            return 4;
        }
        long capacity = (long) Math.ceil(size / 0.75d);
        return (int) Math.min(capacity, Integer.MAX_VALUE);
    }

    private enum Face {
        NEGATIVE_X(-1, 0, 0, new Vec3d(-1.0, 0.0, 0.0)) {
            @Override
            Vec3d[] corners(int x, int y, int z) {
                return new Vec3d[] {
                        point(x, y, z),
                        point(x, y, z + 1),
                        point(x, y + 1, z + 1),
                        point(x, y + 1, z)
                };
            }
        },
        POSITIVE_X(1, 0, 0, new Vec3d(1.0, 0.0, 0.0)) {
            @Override
            Vec3d[] corners(int x, int y, int z) {
                return new Vec3d[] {
                        point(x + 1, y, z),
                        point(x + 1, y + 1, z),
                        point(x + 1, y + 1, z + 1),
                        point(x + 1, y, z + 1)
                };
            }
        },
        NEGATIVE_Y(0, -1, 0, new Vec3d(0.0, -1.0, 0.0)) {
            @Override
            Vec3d[] corners(int x, int y, int z) {
                return new Vec3d[] {
                        point(x, y, z),
                        point(x + 1, y, z),
                        point(x + 1, y, z + 1),
                        point(x, y, z + 1)
                };
            }
        },
        POSITIVE_Y(0, 1, 0, new Vec3d(0.0, 1.0, 0.0)) {
            @Override
            Vec3d[] corners(int x, int y, int z) {
                return new Vec3d[] {
                        point(x, y + 1, z),
                        point(x, y + 1, z + 1),
                        point(x + 1, y + 1, z + 1),
                        point(x + 1, y + 1, z)
                };
            }
        },
        NEGATIVE_Z(0, 0, -1, new Vec3d(0.0, 0.0, -1.0)) {
            @Override
            Vec3d[] corners(int x, int y, int z) {
                return new Vec3d[] {
                        point(x, y, z),
                        point(x, y + 1, z),
                        point(x + 1, y + 1, z),
                        point(x + 1, y, z)
                };
            }
        },
        POSITIVE_Z(0, 0, 1, new Vec3d(0.0, 0.0, 1.0)) {
            @Override
            Vec3d[] corners(int x, int y, int z) {
                return new Vec3d[] {
                        point(x, y, z + 1),
                        point(x + 1, y, z + 1),
                        point(x + 1, y + 1, z + 1),
                        point(x, y + 1, z + 1)
                };
            }
        };

        private final int offsetX;
        private final int offsetY;
        private final int offsetZ;
        private final Vec3d normal;

        Face(int offsetX, int offsetY, int offsetZ, Vec3d normal) {
            this.offsetX = offsetX;
            this.offsetY = offsetY;
            this.offsetZ = offsetZ;
            this.normal = normal;
        }

        abstract Vec3d[] corners(int x, int y, int z);

        BlockPosition neighbour(BlockPosition position) {
            return new BlockPosition(
                    position.x() + offsetX,
                    position.y() + offsetY,
                    position.z() + offsetZ);
        }

        Vec3d normal() {
            return normal;
        }

        static Vec3d point(int x, int y, int z) {
            return new Vec3d(x, y, z);
        }
    }

    private static final class MeshAccumulator {
        private final List<Vec3d> positions = new ArrayList<>();
        private final List<Vec3d> normals = new ArrayList<>();
        private final List<Integer> indices = new ArrayList<>();

        void addFace(BlockPosition position, Face face) {
            int baseIndex = positions.size();
            Vec3d[] corners = face.corners(position.x(), position.y(), position.z());
            for (Vec3d corner : corners) {
                positions.add(corner);
                normals.add(face.normal());
            }
            indices.add(baseIndex);
            indices.add(baseIndex + 1);
            indices.add(baseIndex + 2);
            indices.add(baseIndex);
            indices.add(baseIndex + 2);
            indices.add(baseIndex + 3);
        }

        boolean isEmpty() {
            return positions.isEmpty();
        }

        MeshGroup toMeshGroup(CanonicalIdentifier blockId) {
            String stableId = "mesh/block/" + blockId.namespace() + "/" + blockId.path();
            MeshPrimitive primitive = new MeshPrimitive(
                    positions,
                    normals,
                    indices,
                    Optional.empty(),
                    List.of(),
                    Map.of());
            return new MeshGroup(stableId, blockId.toString(), List.of(primitive), Map.of());
        }
    }
}
