/*
 * SPDX-License-Identifier: MPL-2.0
 */
package dev.sakus.mcad.markers;

import dev.sakus.mcad.api.CameraProjection;
import dev.sakus.mcad.api.CanonicalIdentifier;
import dev.sakus.mcad.api.CollisionKind;
import dev.sakus.mcad.api.Color3d;
import dev.sakus.mcad.api.MetadataValue;
import dev.sakus.mcad.api.SourceReference;
import dev.sakus.mcad.api.Transform;
import dev.sakus.mcad.api.Vec3d;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.TreeMap;
import java.util.regex.Pattern;

/**
 * Neutral scene instructions emitted before mesh generation.
 *
 * <p>These values contain no Minecraft, loader, exporter, file-system, or executable-script objects.</p>
 */
public sealed interface MarkerDirective permits MarkerDirective.Origin, MarkerDirective.PointLight,
        MarkerDirective.Camera, MarkerDirective.Curve, MarkerDirective.Bone, MarkerDirective.Group,
        MarkerDirective.Collision, MarkerDirective.CustomProperty, MarkerDirective.Custom {

    Pattern STABLE_ID = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._:/-]*");

    String stableId();

    List<SourceReference> sources();

    record Origin(String stableId, Vec3d position, List<SourceReference> sources) implements MarkerDirective {
        public Origin {
            stableId = checkedStableId(stableId);
            Objects.requireNonNull(position, "position");
            sources = immutableSources(sources);
        }
    }

    record PointLight(
            String stableId,
            String name,
            Transform transform,
            Color3d colour,
            double intensity,
            OptionalDouble range,
            List<SourceReference> sources) implements MarkerDirective {
        public PointLight {
            stableId = checkedStableId(stableId);
            name = checkedName(name);
            Objects.requireNonNull(transform, "transform");
            Objects.requireNonNull(colour, "colour");
            if (!Double.isFinite(intensity) || intensity < 0.0) {
                throw new IllegalArgumentException("intensity must be finite and non-negative");
            }
            range = Objects.requireNonNull(range, "range");
            range.ifPresent(value -> {
                if (!Double.isFinite(value) || value <= 0.0) {
                    throw new IllegalArgumentException("range must be finite and positive");
                }
            });
            sources = immutableSources(sources);
        }
    }

    record Camera(
            String stableId,
            String name,
            Transform transform,
            CameraProjection projection,
            double nearPlane,
            OptionalDouble farPlane,
            OptionalDouble verticalFieldOfViewRadians,
            OptionalDouble orthographicHeight,
            List<SourceReference> sources) implements MarkerDirective {
        public Camera {
            stableId = checkedStableId(stableId);
            name = checkedName(name);
            Objects.requireNonNull(transform, "transform");
            Objects.requireNonNull(projection, "projection");
            if (!Double.isFinite(nearPlane) || nearPlane <= 0.0) {
                throw new IllegalArgumentException("nearPlane must be finite and positive");
            }
            farPlane = Objects.requireNonNull(farPlane, "farPlane");
            verticalFieldOfViewRadians = Objects.requireNonNull(
                    verticalFieldOfViewRadians, "verticalFieldOfViewRadians");
            orthographicHeight = Objects.requireNonNull(orthographicHeight, "orthographicHeight");
            farPlane.ifPresent(value -> {
                if (!Double.isFinite(value) || value <= nearPlane) {
                    throw new IllegalArgumentException("farPlane must be finite and greater than nearPlane");
                }
            });
            if (projection == CameraProjection.PERSPECTIVE) {
                if (verticalFieldOfViewRadians.isEmpty() || orthographicHeight.isPresent()) {
                    throw new IllegalArgumentException("perspective camera requires FOV only");
                }
                double fov = verticalFieldOfViewRadians.getAsDouble();
                if (!Double.isFinite(fov) || fov <= 0.0 || fov >= Math.PI) {
                    throw new IllegalArgumentException("FOV must be finite and between 0 and PI");
                }
            } else {
                if (orthographicHeight.isEmpty() || verticalFieldOfViewRadians.isPresent()) {
                    throw new IllegalArgumentException("orthographic camera requires height only");
                }
                double height = orthographicHeight.getAsDouble();
                if (!Double.isFinite(height) || height <= 0.0) {
                    throw new IllegalArgumentException("orthographic height must be finite and positive");
                }
            }
            sources = immutableSources(sources);
        }
    }

    record Curve(
            String stableId,
            String name,
            List<Vec3d> controlPoints,
            boolean closed,
            List<SourceReference> sources) implements MarkerDirective {
        public Curve {
            stableId = checkedStableId(stableId);
            name = checkedName(name);
            Objects.requireNonNull(controlPoints, "controlPoints");
            if (controlPoints.size() < 2) {
                throw new IllegalArgumentException("curve requires at least two control points");
            }
            var points = new ArrayList<Vec3d>(controlPoints.size());
            for (Vec3d point : controlPoints) {
                points.add(Objects.requireNonNull(point, "control point"));
            }
            controlPoints = List.copyOf(points);
            sources = immutableOrderedSources(sources);
            if (sources.size() != controlPoints.size()) {
                throw new IllegalArgumentException("curve sources must correspond one-to-one with control points");
            }
        }
    }

    record Bone(
            String stableId,
            String name,
            Optional<String> parentId,
            Transform transform,
            List<SourceReference> sources) implements MarkerDirective {
        public Bone {
            stableId = checkedStableId(stableId);
            name = checkedName(name);
            parentId = Objects.requireNonNull(parentId, "parentId");
            parentId = parentId.map(MarkerDirective::checkedStableId);
            if (parentId.filter(stableId::equals).isPresent()) {
                throw new IllegalArgumentException("bone cannot parent itself");
            }
            Objects.requireNonNull(transform, "transform");
            sources = immutableSources(sources);
        }
    }

    record Group(String stableId, String name, List<SourceReference> sources) implements MarkerDirective {
        public Group {
            stableId = checkedStableId(stableId);
            name = checkedName(name);
            sources = immutableSources(sources);
        }
    }

    record Collision(
            String stableId,
            String name,
            CollisionKind kind,
            Optional<String> targetGroup,
            List<SourceReference> sources) implements MarkerDirective {
        public Collision {
            stableId = checkedStableId(stableId);
            name = checkedName(name);
            Objects.requireNonNull(kind, "kind");
            targetGroup = Objects.requireNonNull(targetGroup, "targetGroup");
            targetGroup = targetGroup.map(MarkerDirective::checkedStableId);
            sources = immutableSources(sources);
        }
    }

    record CustomProperty(
            String stableId,
            CanonicalIdentifier key,
            MetadataValue value,
            List<SourceReference> sources) implements MarkerDirective {
        public CustomProperty {
            stableId = checkedStableId(stableId);
            Objects.requireNonNull(key, "key");
            Objects.requireNonNull(value, "value");
            sources = immutableSources(sources);
        }
    }

    record Custom(
            String stableId,
            CanonicalIdentifier actionId,
            NavigableMap<CanonicalIdentifier, MetadataValue> parameters,
            List<SourceReference> sources) implements MarkerDirective {
        public Custom(
                String stableId,
                CanonicalIdentifier actionId,
                Map<CanonicalIdentifier, MetadataValue> parameters,
                List<SourceReference> sources) {
            this(stableId, actionId, immutableMetadata(parameters), sources);
        }

        public Custom {
            stableId = checkedStableId(stableId);
            Objects.requireNonNull(actionId, "actionId");
            parameters = immutableMetadata(parameters);
            sources = immutableSources(sources);
        }
    }

    private static String checkedStableId(String value) {
        Objects.requireNonNull(value, "stableId");
        if (!STABLE_ID.matcher(value).matches()) {
            throw new IllegalArgumentException("stableId contains unsupported characters: " + value);
        }
        return value;
    }

    private static String checkedName(String value) {
        Objects.requireNonNull(value, "name");
        if (value.isBlank() || value.indexOf('\0') >= 0) {
            throw new IllegalArgumentException("name must be non-blank and contain no NUL");
        }
        return value;
    }

    private static List<SourceReference> immutableSources(List<SourceReference> values) {
        Objects.requireNonNull(values, "sources");
        if (values.isEmpty()) {
            throw new IllegalArgumentException("sources must not be empty");
        }
        var copy = new ArrayList<SourceReference>(values.size());
        var seen = new HashSet<String>();
        for (SourceReference source : values) {
            SourceReference checked = Objects.requireNonNull(source, "source");
            String key = sourceSortKey(checked);
            if (!seen.add(key)) {
                throw new IllegalArgumentException("duplicate source reference: " + key);
            }
            copy.add(checked);
        }
        copy.sort(Comparator.comparing(MarkerDirective::sourceSortKey));
        return List.copyOf(copy);
    }

    private static List<SourceReference> immutableOrderedSources(List<SourceReference> values) {
        Objects.requireNonNull(values, "sources");
        if (values.isEmpty()) {
            throw new IllegalArgumentException("sources must not be empty");
        }
        var copy = new ArrayList<SourceReference>(values.size());
        var seen = new HashSet<String>();
        for (SourceReference source : values) {
            SourceReference checked = Objects.requireNonNull(source, "source");
            String key = sourceSortKey(checked);
            if (!seen.add(key)) {
                throw new IllegalArgumentException("duplicate source reference: " + key);
            }
            copy.add(checked);
        }
        return List.copyOf(copy);
    }

    private static String sourceSortKey(SourceReference source) {
        return source.snapshotId()
                + "|" + source.relativeBlockPosition().map(Object::toString).orElse("")
                + "|" + source.blockId().map(Object::toString).orElse("")
                + "|" + source.markerRuleId().map(Object::toString).orElse("");
    }

    private static NavigableMap<CanonicalIdentifier, MetadataValue> immutableMetadata(
            Map<CanonicalIdentifier, MetadataValue> values) {
        Objects.requireNonNull(values, "parameters");
        var copy = new TreeMap<CanonicalIdentifier, MetadataValue>();
        for (var entry : values.entrySet()) {
            copy.put(
                    Objects.requireNonNull(entry.getKey(), "parameter key"),
                    Objects.requireNonNull(entry.getValue(), "parameter value"));
        }
        return Collections.unmodifiableNavigableMap(copy);
    }
}
