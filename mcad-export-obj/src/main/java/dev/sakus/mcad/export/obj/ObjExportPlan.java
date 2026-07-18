/*
 * SPDX-License-Identifier: MPL-2.0
 */
package dev.sakus.mcad.export.obj;

import dev.sakus.mcad.api.Diagnostic;
import dev.sakus.mcad.api.DiagnosticSeverity;
import dev.sakus.mcad.api.GeneratedScene;
import dev.sakus.mcad.api.MaterialDefinition;
import dev.sakus.mcad.api.MeshGroup;
import dev.sakus.mcad.api.MeshPrimitive;
import dev.sakus.mcad.api.MetadataValue;
import dev.sakus.mcad.api.SceneNode;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;

record ObjExportPlan(
        List<MaterialDefinition> materials,
        Map<String, String> materialNames,
        List<MeshInstance> instances,
        long workUnits) {
    private static final int MAX_NAME_DIAGNOSTICS = 64;

    static ObjExportPlan build(
            GeneratedScene scene,
            ObjExportSettings settings,
            List<Diagnostic> diagnostics) {
        NavigableMap<String, MaterialDefinition> materialById = new TreeMap<>();
        for (MaterialDefinition material : scene.materials()) {
            materialById.put(material.stableId(), material);
        }

        ObjNameAllocator materialAllocator = new ObjNameAllocator();
        NavigableMap<String, String> exportedMaterialNames = new TreeMap<>();
        NameDiagnosticCollector nameDiagnostics = new NameDiagnosticCollector(diagnostics);
        for (MaterialDefinition material : materialById.values()) {
            ObjNameAllocator.Allocation allocation = materialAllocator.allocate(material.name(), "material");
            exportedMaterialNames.put(material.stableId(), allocation.allocated());
            nameDiagnostics.record(allocation);
        }

        NavigableMap<String, MeshGroup> meshById = new TreeMap<>();
        for (MeshGroup mesh : scene.meshes()) {
            meshById.put(mesh.stableId(), mesh);
        }
        NavigableMap<String, SceneNode> nodeById = new TreeMap<>();
        for (SceneNode node : scene.nodes()) {
            nodeById.put(node.stableId(), node);
        }

        Map<String, ObjTransform> worldTransforms = new HashMap<>();
        Set<String> referencedMeshes = new HashSet<>();
        List<InstanceSeed> seeds = new ArrayList<>();
        for (SceneNode node : nodeById.values()) {
            ObjTransform world = worldTransform(node.stableId(), nodeById, worldTransforms, new HashSet<>());
            for (String meshId : node.meshIds()) {
                MeshGroup mesh = meshById.get(meshId);
                if (mesh != null) {
                    seeds.add(new InstanceSeed(node.stableId(), mesh, world));
                    referencedMeshes.add(meshId);
                }
            }
        }
        for (MeshGroup mesh : meshById.values()) {
            if (!referencedMeshes.contains(mesh.stableId())) {
                seeds.add(new InstanceSeed("", mesh, ObjTransform.identity()));
                diagnostics.add(new Diagnostic(
                        DiagnosticSeverity.INFO, ObjDiagnostics.ORPHAN_MESH,
                        "Mesh group is not referenced by a scene node and will be exported with identity transform: "
                                + ObjDiagnostics.bounded(mesh.name()),
                        Optional.empty(),
                        Map.of(ObjDiagnostics.DETAIL_FEATURE,
                                new MetadataValue.StringValue(mesh.stableId()))));
            }
        }
        seeds.sort((left, right) -> {
            int nodeComparison = left.nodeId().compareTo(right.nodeId());
            return nodeComparison != 0
                    ? nodeComparison
                    : left.mesh().stableId().compareTo(right.mesh().stableId());
        });

        ObjTransform global = ObjTransform.global(settings);
        ObjNameAllocator objectAllocator = new ObjNameAllocator();
        List<MeshInstance> instances = new ArrayList<>();
        for (InstanceSeed seed : seeds) {
            ObjNameAllocator.Allocation allocation = objectAllocator.allocate(seed.mesh().name(), "object");
            nameDiagnostics.record(allocation);
            ObjTransform transform = global.after(seed.worldTransform());
            transform.validateForNormals();
            instances.add(new MeshInstance(seed.nodeId(), seed.mesh(), allocation.allocated(), transform));
        }
        nameDiagnostics.finish();

        long workUnits = scene.materials().size();
        for (MeshInstance instance : instances) {
            for (MeshPrimitive primitive : instance.mesh().primitives()) {
                workUnits = Math.addExact(workUnits, primitive.positions().size());
                workUnits = Math.addExact(workUnits, primitive.indices().size() / 3L);
            }
        }
        return new ObjExportPlan(
                List.copyOf(materialById.values()),
                Map.copyOf(exportedMaterialNames),
                List.copyOf(instances),
                workUnits);
    }

    private static ObjTransform worldTransform(
            String nodeId,
            NavigableMap<String, SceneNode> nodeById,
            Map<String, ObjTransform> cache,
            Set<String> active) {
        ObjTransform cached = cache.get(nodeId);
        if (cached != null) {
            return cached;
        }
        if (!active.add(nodeId)) {
            throw new IllegalArgumentException("scene hierarchy contains a cycle at " + nodeId);
        }
        SceneNode node = nodeById.get(nodeId);
        ObjTransform local = ObjTransform.fromTransform(node.localTransform());
        ObjTransform world = node.parentId()
                .map(parent -> worldTransform(parent, nodeById, cache, active).after(local))
                .orElse(local);
        active.remove(nodeId);
        cache.put(nodeId, world);
        return world;
    }

    record MeshInstance(String nodeId, MeshGroup mesh, String objectName, ObjTransform transform) {
    }

    private record InstanceSeed(String nodeId, MeshGroup mesh, ObjTransform worldTransform) {
    }

    private static final class NameDiagnosticCollector {
        private final List<Diagnostic> diagnostics;
        private int emitted;
        private int omitted;

        private NameDiagnosticCollector(List<Diagnostic> diagnostics) {
            this.diagnostics = diagnostics;
        }

        void record(ObjNameAllocator.Allocation allocation) {
            if (!allocation.changed()) {
                return;
            }
            if (emitted >= MAX_NAME_DIAGNOSTICS) {
                omitted++;
                return;
            }
            diagnostics.add(new Diagnostic(
                    DiagnosticSeverity.INFO, ObjDiagnostics.NAME_SUBSTITUTED,
                    allocation.collided()
                            ? "Resolved an OBJ/MTL name collision deterministically"
                            : "Sanitized an OBJ/MTL name",
                    Optional.empty(),
                    Map.of(
                            ObjDiagnostics.DETAIL_REQUESTED_NAME,
                            new MetadataValue.StringValue(ObjDiagnostics.bounded(allocation.requested())),
                            ObjDiagnostics.DETAIL_EXPORTED_NAME,
                            new MetadataValue.StringValue(allocation.allocated()))));
            emitted++;
        }

        void finish() {
            if (omitted > 0) {
                diagnostics.add(new Diagnostic(
                        DiagnosticSeverity.INFO, ObjDiagnostics.NAME_SUBSTITUTED,
                        "Additional OBJ/MTL name substitutions were omitted from diagnostics",
                        Optional.empty(),
                        Map.of(ObjDiagnostics.DETAIL_COUNT, new MetadataValue.LongValue(omitted))));
            }
        }
    }
}
