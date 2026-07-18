/*
 * SPDX-License-Identifier: MPL-2.0
 */
package dev.sakus.mcad.gltf;

import dev.sakus.mcad.api.CameraDefinition;
import dev.sakus.mcad.api.CancellationToken;
import dev.sakus.mcad.api.GeneratedScene;
import dev.sakus.mcad.api.LightDefinition;
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
    private static final String LIGHTS_EXTENSION = "KHR_lights_punctual";

    private final GeneratedScene scene;
    private final GltfOptions options;
    private final CancellationToken cancellation;

    GltfNodeBuilder(GeneratedScene scene, GltfOptions options, CancellationToken cancellation) {
        this.scene = scene;
        this.options = options;
        this.cancellation = cancellation;
    }

    NodeOutput build(
            Map<String, Integer> meshIndices,
            Map<String, Integer> lightIndices,
            Map<String, Integer> cameraIndices) {
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
        addLights(lightIndices, nodes, roots);
        addCameras(cameraIndices, nodes, roots);

        roots = wrapTransform(nodes, roots, SceneContractAdapter.originTransform(scene),
                "m+CAD Scene Origin", "synthetic/scene-origin");
        roots = wrapTransform(nodes, roots, options.transform(),
                "m+CAD Export Transform", "synthetic/export-transform");
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

    private void addLights(
            Map<String, Integer> lightIndices,
            List<Object> nodes,
            List<Integer> roots) {
        for (LightDefinition light : scene.lights()) {
            Integer lightIndex = lightIndices.get(light.stableId());
            if (lightIndex == null) {
                continue;
            }
            cancellation.throwIfCancellationRequested();
            LinkedHashMap<String, Object> node = new LinkedHashMap<>();
            node.put("name", GltfNames.sanitize(light.name(), light.stableId()));
            GltfValueEncoder.putTransform(node, SceneContractAdapter.transform(light));
            LinkedHashMap<String, Object> reference = new LinkedHashMap<>();
            reference.put("light", lightIndex);
            LinkedHashMap<String, Object> extensions = new LinkedHashMap<>();
            extensions.put(LIGHTS_EXTENSION, reference);
            node.put("extensions", extensions);
            node.put("extras", elementExtras(light.stableId(), light));
            roots.add(nodes.size());
            nodes.add(node);
        }
    }

    private void addCameras(
            Map<String, Integer> cameraIndices,
            List<Object> nodes,
            List<Integer> roots) {
        for (CameraDefinition camera : scene.cameras()) {
            Integer cameraIndex = cameraIndices.get(camera.stableId());
            if (cameraIndex == null) {
                continue;
            }
            cancellation.throwIfCancellationRequested();
            LinkedHashMap<String, Object> node = new LinkedHashMap<>();
            node.put("name", GltfNames.sanitize(camera.name(), camera.stableId()));
            GltfValueEncoder.putTransform(node, SceneContractAdapter.transform(camera));
            node.put("camera", cameraIndex);
            node.put("extras", elementExtras(camera.stableId(), camera));
            roots.add(nodes.size());
            nodes.add(node);
        }
    }

    private static LinkedHashMap<String, Object> elementExtras(String stableId, Object element) {
        LinkedHashMap<String, Object> extras = new LinkedHashMap<>();
        extras.put("mcadStableId", stableId);
        List<?> sources = SceneContractAdapter.sourceReferences(element);
        if (!sources.isEmpty()) {
            extras.put("mcadSourceReferences", GltfValueEncoder.neutral(sources));
        }
        return extras;
    }

    private static List<Integer> wrapTransform(
            List<Object> nodes,
            List<Integer> roots,
            Transform transform,
            String name,
            String stableId) {
        if (roots.isEmpty() || transform.equals(Transform.IDENTITY)) {
            return roots;
        }
        LinkedHashMap<String, Object> wrapper = new LinkedHashMap<>();
        wrapper.put("name", name);
        GltfValueEncoder.putTransform(wrapper, transform);
        wrapper.put("children", List.copyOf(roots));
        wrapper.put("extras", Map.of("mcadStableId", stableId));
        List<Integer> wrappedRoots = new ArrayList<>(List.of(nodes.size()));
        nodes.add(wrapper);
        return wrappedRoots;
    }

    record NodeOutput(List<Object> nodes, List<Integer> rootIndices) {
    }
}
