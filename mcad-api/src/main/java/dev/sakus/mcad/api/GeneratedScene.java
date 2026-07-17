/*
 * SPDX-License-Identifier: MPL-2.0
 */
package dev.sakus.mcad.api;


import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

public record GeneratedScene(
        SchemaVersion schemaVersion,
        String sceneId,
        List<String> rootNodeIds,
        List<SceneNode> nodes,
        List<MeshGroup> meshes,
        List<MaterialDefinition> materials,
        List<LightDefinition> lights,
        List<CameraDefinition> cameras,
        List<CurveDefinition> curves,
        List<BoneDefinition> bones,
        List<CollisionDefinition> collisions,
        NavigableMap<CanonicalIdentifier, MetadataValue> customProperties,
        List<Diagnostic> diagnostics,
        SceneStatistics statistics) {

    public GeneratedScene(
            SchemaVersion schemaVersion,
            String sceneId,
            List<String> rootNodeIds,
            List<SceneNode> nodes,
            List<MeshGroup> meshes,
            List<MaterialDefinition> materials,
            List<LightDefinition> lights,
            List<CameraDefinition> cameras,
            List<CurveDefinition> curves,
            List<BoneDefinition> bones,
            List<CollisionDefinition> collisions,
            Map<CanonicalIdentifier, MetadataValue> customProperties,
            List<Diagnostic> diagnostics,
            SceneStatistics statistics) {
        this(schemaVersion, sceneId, rootNodeIds, nodes, meshes, materials, lights, cameras, curves,
                bones, collisions,
                Checks.immutableSortedMap(customProperties, CanonicalIdentifier::compareTo, "customProperties"),
                diagnostics, statistics);
    }

    public GeneratedScene {
        Checks.notNull(schemaVersion, "schemaVersion");
        Checks.stableId(sceneId, "sceneId");
        rootNodeIds = Checks.immutableDistinctSortedStrings(rootNodeIds, "rootNodeIds");
        nodes = stableSort(nodes, SceneNode::stableId, "nodes");
        meshes = stableSort(meshes, MeshGroup::stableId, "meshes");
        materials = stableSort(materials, MaterialDefinition::stableId, "materials");
        lights = stableSort(lights, LightDefinition::stableId, "lights");
        cameras = stableSort(cameras, CameraDefinition::stableId, "cameras");
        curves = stableSort(curves, CurveDefinition::stableId, "curves");
        bones = stableSort(bones, BoneDefinition::stableId, "bones");
        collisions = stableSort(collisions, CollisionDefinition::stableId, "collisions");
        customProperties = Checks.immutableSortedMap(
                customProperties, CanonicalIdentifier::compareTo, "customProperties");
        diagnostics = Checks.immutableSortedList(diagnostics, Diagnostic.STABLE_ORDER, "diagnostics");
        Checks.notNull(statistics, "statistics");

        validateReferences(rootNodeIds, nodes, meshes, materials, bones, collisions);
        validateGlobalIds(nodes, meshes, materials, lights, cameras, curves, bones, collisions);
    }

    private static <T> List<T> stableSort(List<T> values, Function<T, String> idFunction, String name) {
        var copy = new ArrayList<T>(Checks.notNull(values, name));
        copy.sort((left, right) -> idFunction.apply(left).compareTo(idFunction.apply(right)));
        var ids = new HashSet<String>();
        for (T value : copy) {
            Checks.notNull(value, name + " value");
            if (!ids.add(idFunction.apply(value))) {
                throw new IllegalArgumentException("duplicate " + name + " stableId: " + idFunction.apply(value));
            }
        }
        return List.copyOf(copy);
    }

    private static void validateGlobalIds(
            List<SceneNode> nodes,
            List<MeshGroup> meshes,
            List<MaterialDefinition> materials,
            List<LightDefinition> lights,
            List<CameraDefinition> cameras,
            List<CurveDefinition> curves,
            List<BoneDefinition> bones,
            List<CollisionDefinition> collisions) {
        var ids = new HashSet<String>();
        addIds(ids, nodes, SceneNode::stableId, "node");
        addIds(ids, meshes, MeshGroup::stableId, "mesh");
        addIds(ids, materials, MaterialDefinition::stableId, "material");
        addIds(ids, lights, LightDefinition::stableId, "light");
        addIds(ids, cameras, CameraDefinition::stableId, "camera");
        addIds(ids, curves, CurveDefinition::stableId, "curve");
        addIds(ids, bones, BoneDefinition::stableId, "bone");
        addIds(ids, collisions, CollisionDefinition::stableId, "collision");
    }

    private static <T> void addIds(Set<String> ids, List<T> values, Function<T, String> idFunction, String type) {
        for (T value : values) {
            String id = idFunction.apply(value);
            if (!ids.add(id)) {
                throw new IllegalArgumentException("stableId is not globally unique (" + type + "): " + id);
            }
        }
    }

    private static void validateReferences(
            List<String> rootNodeIds,
            List<SceneNode> nodes,
            List<MeshGroup> meshes,
            List<MaterialDefinition> materials,
            List<BoneDefinition> bones,
            List<CollisionDefinition> collisions) {
        var nodeById = index(nodes, SceneNode::stableId);
        var meshById = index(meshes, MeshGroup::stableId);
        var materialById = index(materials, MaterialDefinition::stableId);

        var expectedRoots = new HashSet<String>();
        for (SceneNode node : nodes) {
            if (node.parentId().isEmpty()) {
                expectedRoots.add(node.stableId());
            } else {
                SceneNode parent = nodeById.get(node.parentId().orElseThrow());
                if (parent == null || !parent.childIds().contains(node.stableId())) {
                    throw new IllegalArgumentException("node parent/child relationship is inconsistent: " + node.stableId());
                }
            }
            for (String childId : node.childIds()) {
                SceneNode child = nodeById.get(childId);
                if (child == null || !child.parentId().equals(Optional.of(node.stableId()))) {
                    throw new IllegalArgumentException("node child/parent relationship is inconsistent: " + childId);
                }
            }
            for (String meshId : node.meshIds()) {
                if (!meshById.containsKey(meshId)) {
                    throw new IllegalArgumentException("node references unknown mesh: " + meshId);
                }
            }
            detectParentCycle(node.stableId(), nodeById);
        }
        if (!expectedRoots.equals(new HashSet<>(rootNodeIds))) {
            throw new IllegalArgumentException("rootNodeIds must exactly match nodes without parents");
        }

        for (MeshGroup mesh : meshes) {
            for (MeshPrimitive primitive : mesh.primitives()) {
                primitive.materialId().ifPresent(materialId -> {
                    if (!materialById.containsKey(materialId)) {
                        throw new IllegalArgumentException("primitive references unknown material: " + materialId);
                    }
                });
            }
        }
        for (CollisionDefinition collision : collisions) {
            for (String meshId : collision.meshIds()) {
                if (!meshById.containsKey(meshId)) {
                    throw new IllegalArgumentException("collision references unknown mesh: " + meshId);
                }
            }
        }

        var boneById = index(bones, BoneDefinition::stableId);
        for (BoneDefinition bone : bones) {
            bone.parentId().ifPresent(parentId -> {
                if (!boneById.containsKey(parentId)) {
                    throw new IllegalArgumentException("bone references unknown parent: " + parentId);
                }
            });
            detectBoneCycle(bone.stableId(), boneById);
        }
    }

    private static void detectParentCycle(String start, Map<String, SceneNode> nodes) {
        var seen = new HashSet<String>();
        String current = start;
        while (current != null) {
            if (!seen.add(current)) {
                throw new IllegalArgumentException("node hierarchy contains a cycle at: " + current);
            }
            SceneNode node = nodes.get(current);
            current = node == null ? null : node.parentId().orElse(null);
        }
    }

    private static void detectBoneCycle(String start, Map<String, BoneDefinition> bones) {
        var seen = new HashSet<String>();
        String current = start;
        while (current != null) {
            if (!seen.add(current)) {
                throw new IllegalArgumentException("bone hierarchy contains a cycle at: " + current);
            }
            BoneDefinition bone = bones.get(current);
            current = bone == null ? null : bone.parentId().orElse(null);
        }
    }

    private static <T> Map<String, T> index(List<T> values, Function<T, String> idFunction) {
        var result = new HashMap<String, T>();
        for (T value : values) {
            result.put(idFunction.apply(value), value);
        }
        return result;
    }
}
