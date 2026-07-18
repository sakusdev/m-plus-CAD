/*
 * SPDX-License-Identifier: MPL-2.0
 */
package dev.sakus.mcad.gltf;

import dev.sakus.mcad.api.CancellationToken;
import dev.sakus.mcad.api.GeneratedScene;
import dev.sakus.mcad.api.MeshGroup;
import dev.sakus.mcad.api.SceneNode;
import dev.sakus.mcad.api.Transform;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class GltfNodeBuilder {
    private final GeneratedScene scene;
    private final GltfOptions options;
    private final CancellationToken cancellation;

    GltfNodeBuilder(GeneratedScene scene, GltfOptions options, CancellationToken cancellation) {
        this.scene = scene;
        this.options = options;
        this.cancellation = cancellation;
    }

    NodeOutput build(Map<String, Integer> meshIndices) {
        List<Object> nodes = new ArrayList<>();
        Map<String, Integer> nodeIndices = new HashMap<>();
        for (SceneNode node : scene.nodes()) {
            cancellation.throwIfCancellationRequested();
            nodeIndices.put(node.stableId(), nodes.size());
            LinkedHashMap<String, Object> output = new LinkedHashMap<>();
            output.put("name", GltfNames.sanitize(node.name(), node.stableId()));
            GltfValueEncoder.putTransform(output, node.localTransform());
            LinkedHashMap<String, Object> extras = GltfValueEncoder.baseExtras(
                    node.stableId(), node.customProperties());
            if (!node.sourceReferences().isEmpty()) {
                extras.put("mcadSourceReferences", GltfValueEncoder.neutral(node.sourceReferences()));
            }
            output.put("extras", extras);
            nodes.add(output);
        }

        Set<String> referencedMeshes = new HashSet<>();
        for (SceneNode node : scene.nodes()) {
            @SuppressWarnings("unchecked")
            LinkedHashMap<String, Object> output =
                    (LinkedHashMap<String, Object>) nodes.get(nodeIndices.get(node.stableId()));
            List<Integer> children = new ArrayList<>();
            for (String childId : node.childIds()) {
                children.add(nodeIndices.get(childId));
            }
            if (node.meshIds().size() == 1) {
                String meshId = node.meshIds().getFirst();
                output.put("mesh", meshIndices.get(meshId));
                referencedMeshes.add(meshId);
            } else {
                addMeshAttachments(node, meshIndices, nodes, children, referencedMeshes);
            }
            if (!children.isEmpty()) {
                output.put("children", List.copyOf(children));
            }
        }

        List<Integer> roots = new ArrayList<>();
        for (String rootNodeId : scene.rootNodeIds()) {
            roots.add(nodeIndices.get(rootNodeId));
        }
        addUnattachedMeshes(meshIndices, nodes, roots, referencedMeshes);
        if (!roots.isEmpty() && !options.transform().equals(Transform.IDENTITY)) {
            LinkedHashMap<String, Object> exportRoot = new LinkedHashMap<>();
            exportRoot.put("name", "m+CAD Export Transform");
            GltfValueEncoder.putTransform(exportRoot, options.transform());
            exportRoot.put("children", List.copyOf(roots));
            exportRoot.put("extras", Map.of("mcadStableId", "synthetic/export-transform"));
            roots = new ArrayList<>(List.of(nodes.size()));
            nodes.add(exportRoot);
        }
        return new NodeOutput(List.copyOf(nodes), List.copyOf(roots));
    }

    private void addMeshAttachments(
            SceneNode node,
            Map<String, Integer> meshIndices,
            List<Object> nodes,
            List<Integer> children,
            Set<String> referencedMeshes) {
        for (String meshId : node.meshIds()) {
            referencedMeshes.add(meshId);
            LinkedHashMap<String, Object> attachment = new LinkedHashMap<>();
            attachment.put("name", GltfNames.sanitize(meshId, meshId));
            attachment.put("mesh", meshIndices.get(meshId));
            attachment.put("extras", GltfValueEncoder.attachmentExtras(
                    node.stableId() + "/mesh-attachment/" + meshId, meshId));
            children.add(nodes.size());
            nodes.add(attachment);
        }
    }

    private void addUnattachedMeshes(
            Map<String, Integer> meshIndices,
            List<Object> nodes,
            List<Integer> roots,
            Set<String> referencedMeshes) {
        for (MeshGroup mesh : scene.meshes()) {
            if (!referencedMeshes.contains(mesh.stableId())) {
                LinkedHashMap<String, Object> unattached = new LinkedHashMap<>();
                unattached.put("name", GltfNames.sanitize(mesh.name(), mesh.stableId()));
                unattached.put("mesh", meshIndices.get(mesh.stableId()));
                unattached.put("extras", GltfValueEncoder.attachmentExtras(
                        "synthetic/unattached/" + mesh.stableId(), mesh.stableId()));
                roots.add(nodes.size());
                nodes.add(unattached);
            }
        }
    }

    record NodeOutput(List<Object> nodes, List<Integer> rootIndices) {
    }
}
