/*
 * SPDX-License-Identifier: MPL-2.0
 */
package dev.sakus.mcad.minecraft.runtime;

import dev.sakus.mcad.api.BoneDefinition;
import dev.sakus.mcad.api.CameraDefinition;
import dev.sakus.mcad.api.CanonicalIdentifier;
import dev.sakus.mcad.api.CollisionDefinition;
import dev.sakus.mcad.api.CollisionKind;
import dev.sakus.mcad.api.Diagnostic;
import dev.sakus.mcad.api.DiagnosticSeverity;
import dev.sakus.mcad.api.GeneratedScene;
import dev.sakus.mcad.api.LightDefinition;
import dev.sakus.mcad.api.LightType;
import dev.sakus.mcad.api.MaterialDefinition;
import dev.sakus.mcad.api.MeshGroup;
import dev.sakus.mcad.api.MeshPrimitive;
import dev.sakus.mcad.api.MetadataValue;
import dev.sakus.mcad.api.ProjectSettings;
import dev.sakus.mcad.api.Quaterniond;
import dev.sakus.mcad.api.SceneNode;
import dev.sakus.mcad.api.SceneStatistics;
import dev.sakus.mcad.api.Transform;
import dev.sakus.mcad.api.Vec3d;
import dev.sakus.mcad.markers.MarkerDirective;
import dev.sakus.mcad.markers.MarkerInterpretationResult;
import dev.sakus.mcad.materials.MaterialMode;
import dev.sakus.mcad.materials.MaterialResolution;
import dev.sakus.mcad.materials.MaterialResolver;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.Set;

/** Resolves material and marker results into the immutable neutral generated-scene contract. */
public final class SceneAssembler {
    private static final CanonicalIdentifier UNKNOWN_MESH_ID =
            CanonicalIdentifier.parse("mcad:pipeline/unknown_mesh_block_id");
    private static final CanonicalIdentifier COLLISION_FALLBACK =
            CanonicalIdentifier.parse("mcad:pipeline/collision_target_fallback");
    private static final CanonicalIdentifier EMPTY_COLLISION =
            CanonicalIdentifier.parse("mcad:pipeline/collision_omitted_empty_scene");

    private SceneAssembler() {
    }

    public static GeneratedScene assemble(
            GeneratedScene generated,
            ProjectSettings settings,
            MarkerInterpretationResult markerResult,
            MaterialResolver materialResolver) {
        Objects.requireNonNull(generated, "generated");
        Objects.requireNonNull(settings, "settings");
        Objects.requireNonNull(markerResult, "markerResult");
        Objects.requireNonNull(materialResolver, "materialResolver");

        var diagnostics = new ArrayList<Diagnostic>(generated.diagnostics());
        diagnostics.addAll(markerResult.diagnostics());

        MaterialMode materialMode = materialMode(settings.materials().mode());
        var materialById = new LinkedHashMap<String, MaterialDefinition>();
        var meshes = new ArrayList<MeshGroup>(generated.meshes().size());
        for (MeshGroup mesh : generated.meshes()) {
            MaterialResolution resolution = resolveMaterial(mesh, materialResolver, materialMode, diagnostics);
            resolution.diagnostics().forEach(diagnostics::add);
            resolution.material().ifPresent(material -> materialById.putIfAbsent(material.stableId(), material));

            var primitives = new ArrayList<MeshPrimitive>(mesh.primitives().size());
            for (MeshPrimitive primitive : mesh.primitives()) {
                Optional<String> materialId = resolution.material().map(MaterialDefinition::stableId);
                List<dev.sakus.mcad.api.Color4d> vertexColours = resolution.vertexColour()
                        .map(colour -> java.util.Collections.nCopies(primitive.positions().size(), colour))
                        .orElseGet(primitive::vertexColours);
                primitives.add(new MeshPrimitive(
                        primitive.positions(),
                        primitive.normals(),
                        primitive.indices(),
                        materialId,
                        vertexColours,
                        primitive.customAttributes()));
            }
            meshes.add(new MeshGroup(
                    mesh.stableId(),
                    mesh.name(),
                    primitives,
                    mesh.customProperties(),
                    mesh.sourceReferences()));
        }

        Transform originTransform = generated.originTransform();
        var lights = new ArrayList<>(generated.lights());
        var cameras = new ArrayList<>(generated.cameras());
        var curves = new ArrayList<>(generated.curves());
        var bones = new ArrayList<>(generated.bones());
        var collisions = new ArrayList<>(generated.collisions());
        var customProperties = new LinkedHashMap<>(generated.customProperties());
        var groupNodes = new ArrayList<SceneNode>();

        String rootId = generated.rootNodeIds().isEmpty() ? null : generated.rootNodeIds().getFirst();
        Set<String> meshIds = new LinkedHashSet<>();
        meshes.forEach(mesh -> meshIds.add(mesh.stableId()));

        for (MarkerDirective directive : markerResult.directives()) {
            switch (directive) {
                case MarkerDirective.Origin origin -> originTransform = new Transform(
                        new Vec3d(-origin.position().x(), -origin.position().y(), -origin.position().z()),
                        Quaterniond.IDENTITY,
                        Vec3d.ONE);
                case MarkerDirective.PointLight light -> lights.add(new LightDefinition(
                        light.stableId(),
                        light.name(),
                        light.transform(),
                        LightType.POINT,
                        light.colour(),
                        light.intensity(),
                        light.range(),
                        OptionalDouble.empty(),
                        OptionalDouble.empty(),
                        light.sources()));
                case MarkerDirective.Camera camera -> cameras.add(new CameraDefinition(
                        camera.stableId(),
                        camera.name(),
                        camera.transform(),
                        camera.projection(),
                        camera.nearPlane(),
                        camera.farPlane(),
                        camera.verticalFieldOfViewRadians(),
                        camera.orthographicHeight(),
                        camera.sources()));
                case MarkerDirective.Curve curve -> curves.add(new dev.sakus.mcad.api.CurveDefinition(
                        curve.stableId(), curve.name(), curve.controlPoints(), curve.closed(), curve.sources()));
                case MarkerDirective.Bone bone -> bones.add(new BoneDefinition(
                        bone.stableId(), bone.name(), bone.parentId(), bone.transform(), bone.sources()));
                case MarkerDirective.Group group -> {
                    if (rootId != null) {
                        groupNodes.add(new SceneNode(
                                group.stableId(),
                                group.name(),
                                Optional.of(rootId),
                                Transform.IDENTITY,
                                List.of(),
                                List.of(),
                                Map.of(),
                                group.sources()));
                    }
                }
                case MarkerDirective.Collision collision -> addCollision(
                        collision, meshIds, collisions, diagnostics);
                case MarkerDirective.CustomProperty property ->
                        customProperties.put(property.key(), property.value());
                case MarkerDirective.Custom custom -> customProperties.put(
                        CanonicalIdentifier.parse("mcad:marker_custom/"
                                + custom.actionId().namespace() + "/" + custom.actionId().path()),
                        new MetadataValue.MapValue(custom.parameters()));
            }
        }

        var nodes = attachGroups(generated.nodes(), rootId, groupNodes);
        SceneStatistics previous = generated.statistics();
        SceneStatistics statistics = new SceneStatistics(
                previous.sourceBlockCount(),
                previous.visibleFaceCount(),
                previous.removedHiddenFaceCount(),
                previous.vertexCount(),
                previous.triangleCount(),
                meshes.size(),
                materialById.size());

        return new GeneratedScene(
                generated.schemaVersion(),
                generated.sceneId(),
                originTransform,
                generated.rootNodeIds(),
                nodes,
                meshes,
                List.copyOf(materialById.values()),
                lights,
                cameras,
                curves,
                bones,
                collisions,
                customProperties,
                diagnostics,
                statistics);
    }

    private static MaterialResolution resolveMaterial(
            MeshGroup mesh,
            MaterialResolver resolver,
            MaterialMode mode,
            List<Diagnostic> diagnostics) {
        try {
            return resolver.resolve(CanonicalIdentifier.parse(mesh.name()), mode);
        } catch (IllegalArgumentException exception) {
            diagnostics.add(new Diagnostic(
                    DiagnosticSeverity.WARNING,
                    UNKNOWN_MESH_ID,
                    "Mesh name is not a canonical block identifier; no material was assigned: " + mesh.name(),
                    Optional.empty(),
                    Map.of()));
            return resolver.resolve(CanonicalIdentifier.parse("mcad:unknown"), MaterialMode.NONE);
        }
    }

    private static MaterialMode materialMode(ProjectSettings.MaterialMode mode) {
        return switch (mode) {
            case NONE -> MaterialMode.NONE;
            case ORIGINAL_SINGLE_COLOUR -> MaterialMode.BUILT_IN_SINGLE_COLOUR;
            case VERTEX_COLOURS -> MaterialMode.VERTEX_COLOUR;
            case DETERMINISTIC_IDENTIFICATION_COLOURS -> MaterialMode.IDENTIFICATION_COLOUR;
            case USER_DEFINED_MAPPING -> MaterialMode.USER_MAPPING;
        };
    }

    private static void addCollision(
            MarkerDirective.Collision collision,
            Set<String> meshIds,
            List<CollisionDefinition> collisions,
            List<Diagnostic> diagnostics) {
        if (meshIds.isEmpty()) {
            diagnostics.add(new Diagnostic(
                    DiagnosticSeverity.WARNING,
                    EMPTY_COLLISION,
                    "Collision marker was omitted because the scene has no meshes: " + collision.name(),
                    Optional.empty(),
                    Map.of()));
            return;
        }

        List<String> targets;
        if (collision.targetGroup().isPresent() && meshIds.contains(collision.targetGroup().orElseThrow())) {
            targets = List.of(collision.targetGroup().orElseThrow());
        } else {
            targets = List.copyOf(meshIds);
            if (collision.targetGroup().isPresent()) {
                diagnostics.add(new Diagnostic(
                        DiagnosticSeverity.WARNING,
                        COLLISION_FALLBACK,
                        "Collision target group could not be resolved; all generated meshes were used",
                        Optional.empty(),
                        Map.of()));
            }
        }
        CollisionKind kind = collision.kind();
        collisions.add(new CollisionDefinition(
                collision.stableId(), collision.name(), kind, targets, collision.sources()));
    }

    private static List<SceneNode> attachGroups(
            List<SceneNode> original,
            String rootId,
            List<SceneNode> groups) {
        if (rootId == null || groups.isEmpty()) {
            return original;
        }
        List<String> newChildren = groups.stream().map(SceneNode::stableId).toList();
        var nodes = new ArrayList<SceneNode>(original.size() + groups.size());
        for (SceneNode node : original) {
            if (!node.stableId().equals(rootId)) {
                nodes.add(node);
                continue;
            }
            var childIds = new ArrayList<>(node.childIds());
            childIds.addAll(newChildren);
            nodes.add(new SceneNode(
                    node.stableId(),
                    node.name(),
                    node.parentId(),
                    node.localTransform(),
                    node.meshIds(),
                    childIds,
                    node.customProperties(),
                    node.sourceReferences()));
        }
        nodes.addAll(groups);
        return List.copyOf(nodes);
    }
}
