/*
 * SPDX-License-Identifier: MPL-2.0
 */
package dev.sakus.mcad.api;


import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Optional;

public record ProjectSettings(
        SchemaVersion schemaVersion,
        SelectionSettings selection,
        GeometrySettings geometry,
        MeshSeparationSettings meshSeparation,
        MaterialSettings materials,
        TransformSettings transform,
        MarkerSettings markers,
        OptimizationSettings optimization,
        AnimationSettings animation,
        CollisionSettings collision,
        OutputSettings output,
        PreviewSettings preview,
        AdvancedSettings advanced) {

    public ProjectSettings {
        Checks.notNull(schemaVersion, "schemaVersion");
        Checks.notNull(selection, "selection");
        Checks.notNull(geometry, "geometry");
        Checks.notNull(meshSeparation, "meshSeparation");
        Checks.notNull(materials, "materials");
        Checks.notNull(transform, "transform");
        Checks.notNull(markers, "markers");
        Checks.notNull(optimization, "optimization");
        Checks.notNull(animation, "animation");
        Checks.notNull(collision, "collision");
        Checks.notNull(output, "output");
        Checks.notNull(preview, "preview");
        Checks.notNull(advanced, "advanced");
    }

    public record SelectionSettings(long maximumBlockCount, boolean preserveEmptyCells) {
        public SelectionSettings {
            Checks.positive(maximumBlockCount, "maximumBlockCount");
        }
    }

    public record GeometrySettings(boolean hiddenFaceRemoval, boolean rejectDegenerateTriangles) {
    }

    public enum SeparationKey {
        USER_GROUP,
        BLOCK_IDENTIFIER,
        BLOCK_STATE,
        CONNECTED_COMPONENT,
        MATERIAL_IDENTIFIER,
        CHUNK_TILE
    }

    public record MeshSeparationSettings(List<SeparationKey> orderedKeys) {
        public MeshSeparationSettings {
            orderedKeys = Checks.immutableList(orderedKeys, "orderedKeys");
            Checks.requireNoDuplicates(orderedKeys, "orderedKeys");
        }
    }

    public enum MaterialMode {
        NONE,
        ORIGINAL_SINGLE_COLOUR,
        VERTEX_COLOURS,
        DETERMINISTIC_IDENTIFICATION_COLOURS,
        USER_DEFINED_MAPPING
    }

    public record MaterialSettings(MaterialMode mode, Optional<String> userMappingId) {
        public MaterialSettings {
            Checks.notNull(mode, "mode");
            userMappingId = Checks.notNull(userMappingId, "userMappingId");
            userMappingId.ifPresent(value -> Checks.stableId(value, "userMappingId"));
            if (mode != MaterialMode.USER_DEFINED_MAPPING && userMappingId.isPresent()) {
                throw new IllegalArgumentException("userMappingId requires USER_DEFINED_MAPPING mode");
            }
        }
    }

    public enum OriginMode {
        SELECTION_MINIMUM,
        SELECTION_CENTRE,
        BOTTOM_CENTRE,
        CAPTURED_PLAYER_POSITION,
        MARKER_DEFINED,
        PRESERVED_WORLD_ORIGIN,
        EXPLICIT_OFFSET
    }

    public enum AxisConvention {
        INTERNAL_RIGHT_HANDED_Y_UP,
        RIGHT_HANDED_Z_UP,
        LEFT_HANDED_Y_UP
    }

    public record TransformSettings(
            OriginMode originMode,
            Vec3d explicitOriginOffset,
            Vec3d rotationDegrees,
            double unitScale,
            AxisConvention targetAxis) {
        public TransformSettings {
            Checks.notNull(originMode, "originMode");
            Checks.notNull(explicitOriginOffset, "explicitOriginOffset");
            Checks.notNull(rotationDegrees, "rotationDegrees");
            Checks.finite(unitScale, "unitScale");
            if (unitScale <= 0.0) {
                throw new IllegalArgumentException("unitScale must be positive");
            }
            Checks.notNull(targetAxis, "targetAxis");
        }
    }

    public record MarkerSettings(boolean enabled, boolean consumeSemanticSources, boolean previewInterpretation) {
    }

    public record OptimizationSettings(boolean greedyMeshing, boolean instancing, boolean preserveMaterialBoundaries) {
    }

    public record AnimationSettings(boolean enabled, int framesPerSecond) {
        public AnimationSettings {
            if (framesPerSecond <= 0 || framesPerSecond > 1000) {
                throw new IllegalArgumentException("framesPerSecond must be in [1, 1000]");
            }
        }
    }

    public record CollisionSettings(boolean enabled, CollisionKind defaultKind) {
        public CollisionSettings {
            Checks.notNull(defaultKind, "defaultKind");
        }
    }

    public enum LossPolicy {
        FAIL,
        WARN_AND_CONTINUE
    }

    public record OutputSettings(
            CanonicalIdentifier exporterId,
            String destination,
            LossPolicy lossPolicy,
            NavigableMap<CanonicalIdentifier, MetadataValue> exporterOptions) {

        public OutputSettings(
                CanonicalIdentifier exporterId,
                String destination,
                LossPolicy lossPolicy,
                Map<CanonicalIdentifier, MetadataValue> exporterOptions) {
            this(exporterId, destination, lossPolicy,
                    Checks.immutableSortedMap(exporterOptions, CanonicalIdentifier::compareTo, "exporterOptions"));
        }

        public OutputSettings {
            Checks.notNull(exporterId, "exporterId");
            Checks.nonBlank(destination, "destination");
            Checks.notNull(lossPolicy, "lossPolicy");
            exporterOptions = Checks.immutableSortedMap(
                    exporterOptions, CanonicalIdentifier::compareTo, "exporterOptions");
        }
    }

    public record PreviewSettings(boolean selectionOutline, boolean diagnosticsOverlay, boolean consumedMarkers) {
    }

    public record AdvancedSettings(int workerThreads, long memoryLimitBytes) {
        public AdvancedSettings {
            Checks.positive(workerThreads, "workerThreads");
            Checks.positive(memoryLimitBytes, "memoryLimitBytes");
        }
    }
}
