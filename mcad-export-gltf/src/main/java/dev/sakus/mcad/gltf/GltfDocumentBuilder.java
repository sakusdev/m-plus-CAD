/*
 * SPDX-License-Identifier: MPL-2.0
 */
package dev.sakus.mcad.gltf;

import dev.sakus.mcad.api.AlphaMode;
import dev.sakus.mcad.api.CancellationToken;
import dev.sakus.mcad.api.CanonicalIdentifier;
import dev.sakus.mcad.api.Color3d;
import dev.sakus.mcad.api.GeneratedScene;
import dev.sakus.mcad.api.MaterialDefinition;
import dev.sakus.mcad.api.MeshGroup;
import dev.sakus.mcad.api.MeshPrimitive;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

final class GltfDocumentBuilder {
    private final GeneratedScene scene;
    private final GltfOptions options;
    private final CancellationToken cancellation;
    private final BinaryBufferBuilder binary = new BinaryBufferBuilder();
    private final Set<String> extensionsUsed = new TreeSet<>();
    private final Set<String> extensionsRequired = new TreeSet<>();

    GltfDocumentBuilder(GeneratedScene scene, GltfOptions options, CancellationToken cancellation) {
        this.scene = scene;
        this.options = options;
        this.cancellation = cancellation;
    }

    GltfBuildOutput build(GltfDestination destination) {
        LinkedHashMap<String, Object> document = new LinkedHashMap<>();
        document.put("asset", asset());

        Map<String, Integer> materialIndices = new HashMap<>();
        List<Object> materials = buildMaterials(materialIndices);
        if (!materials.isEmpty()) {
            document.put("materials", materials);
        }

        Map<String, Integer> meshIndices = new HashMap<>();
        List<Object> meshes = buildMeshes(materialIndices, meshIndices);
        if (!meshes.isEmpty()) {
            document.put("meshes", meshes);
        }

        GltfNodeBuilder.NodeOutput nodeOutput =
                new GltfNodeBuilder(scene, options, cancellation).build(meshIndices);
        if (!nodeOutput.nodes().isEmpty()) {
            document.put("nodes", nodeOutput.nodes());
        }
        LinkedHashMap<String, Object> gltfScene = new LinkedHashMap<>();
        if (!nodeOutput.rootIndices().isEmpty()) {
            gltfScene.put("nodes", nodeOutput.rootIndices());
        }
        gltfScene.put("extras", sceneExtras());
        document.put("scenes", List.of(gltfScene));
        document.put("scene", 0);

        byte[] binaryBytes = binary.bytes();
        if (binaryBytes.length > 0) {
            LinkedHashMap<String, Object> buffer = new LinkedHashMap<>();
            buffer.put("byteLength", binaryBytes.length);
            if (destination.format() == GltfDestination.Format.GLTF) {
                buffer.put("uri", destination.binaryUri());
            }
            document.put("buffers", List.of(buffer));
            document.put("bufferViews", binary.bufferViews());
            document.put("accessors", binary.accessors());
        }
        if (!extensionsUsed.isEmpty()) {
            document.put("extensionsUsed", List.copyOf(extensionsUsed));
        }
        if (!extensionsRequired.isEmpty()) {
            document.put("extensionsRequired", List.copyOf(extensionsRequired));
        }

        byte[] jsonBytes = JsonWriter.write(document).getBytes(StandardCharsets.UTF_8);
        byte[] glbBytes = destination.format() == GltfDestination.Format.GLB
                ? GlbWriter.write(jsonBytes, binaryBytes)
                : new byte[0];
        return new GltfBuildOutput(jsonBytes, binaryBytes, glbBytes);
    }

    private Object asset() {
        LinkedHashMap<String, Object> asset = new LinkedHashMap<>();
        asset.put("version", "2.0");
        asset.put("generator", "m+CAD glTF exporter");
        LinkedHashMap<String, Object> extras = new LinkedHashMap<>();
        extras.put("mcadSceneId", scene.sceneId());
        extras.put("mcadSceneSchemaVersion", scene.schemaVersion().toString());
        asset.put("extras", extras);
        return asset;
    }

    private List<Object> buildMaterials(Map<String, Integer> materialIndices) {
        List<Object> result = new ArrayList<>();
        for (MaterialDefinition material : scene.materials()) {
            cancellation.throwIfCancellationRequested();
            materialIndices.put(material.stableId(), result.size());
            LinkedHashMap<String, Object> output = new LinkedHashMap<>();
            output.put("name", GltfNames.sanitize(material.name(), material.stableId()));

            LinkedHashMap<String, Object> pbr = new LinkedHashMap<>();
            pbr.put("baseColorFactor", GltfValueEncoder.color(material.baseColour()));
            pbr.put("metallicFactor", material.metallic());
            pbr.put("roughnessFactor", material.roughness());
            output.put("pbrMetallicRoughness", pbr);
            if (material.alphaMode() != AlphaMode.OPAQUE) {
                output.put("alphaMode", material.alphaMode().name());
            }
            material.alphaCutoff().ifPresent(value -> output.put("alphaCutoff", value));
            addEmission(output, material);

            LinkedHashMap<String, Object> extras = GltfValueEncoder.baseExtras(
                    material.stableId(), material.customProperties());
            if (!material.externalUserAssetReferences().isEmpty()) {
                extras.put("mcadExternalUserAssetReferences",
                        GltfValueEncoder.neutral(material.externalUserAssetReferences()));
            }
            output.put("extras", extras);
            result.add(output);
        }
        return List.copyOf(result);
    }

    private void addEmission(LinkedHashMap<String, Object> output, MaterialDefinition material) {
        Color3d emissive = material.emissiveColour();
        if (material.emissiveStrength() <= 0.0
                || (emissive.red() == 0.0 && emissive.green() == 0.0 && emissive.blue() == 0.0)) {
            return;
        }
        output.put("emissiveFactor", List.of(emissive.red(), emissive.green(), emissive.blue()));
        if (Double.compare(material.emissiveStrength(), 1.0) != 0) {
            extensionsUsed.add("KHR_materials_emissive_strength");
            extensionsRequired.add("KHR_materials_emissive_strength");
            output.put("extensions", Map.of(
                    "KHR_materials_emissive_strength",
                    Map.of("emissiveStrength", material.emissiveStrength())));
        }
    }

    private List<Object> buildMeshes(
            Map<String, Integer> materialIndices,
            Map<String, Integer> meshIndices) {
        List<Object> result = new ArrayList<>();
        for (MeshGroup mesh : scene.meshes()) {
            cancellation.throwIfCancellationRequested();
            meshIndices.put(mesh.stableId(), result.size());
            LinkedHashMap<String, Object> output = new LinkedHashMap<>();
            output.put("name", GltfNames.sanitize(mesh.name(), mesh.stableId()));
            List<Object> primitives = new ArrayList<>();
            for (int index = 0; index < mesh.primitives().size(); index++) {
                cancellation.throwIfCancellationRequested();
                primitives.add(buildPrimitive(mesh.primitives().get(index), index, materialIndices));
            }
            output.put("primitives", primitives);
            output.put("extras", GltfValueEncoder.baseExtras(mesh.stableId(), mesh.customProperties()));
            result.add(output);
        }
        return List.copyOf(result);
    }

    private Object buildPrimitive(
            MeshPrimitive primitive,
            int primitiveIndex,
            Map<String, Integer> materialIndices) {
        LinkedHashMap<String, Object> attributes = new LinkedHashMap<>();
        attributes.put("POSITION", binary.addFloatVec3(
                GltfValueEncoder.vectors(primitive.positions()), true, BinaryBufferBuilder.ARRAY_BUFFER));
        attributes.put("NORMAL", binary.addFloatVec3(
                GltfValueEncoder.normalizedVectors(primitive.normals()), false,
                BinaryBufferBuilder.ARRAY_BUFFER));
        if (!primitive.vertexColours().isEmpty()) {
            attributes.put("COLOR_0", binary.addFloatVec4(
                    GltfValueEncoder.colours(primitive.vertexColours()), BinaryBufferBuilder.ARRAY_BUFFER));
        }

        LinkedHashMap<String, Object> customSemanticMap = new LinkedHashMap<>();
        for (Map.Entry<CanonicalIdentifier, List<Double>> entry : primitive.customAttributes().entrySet()) {
            String semantic = GltfNames.attributeSemantic(entry.getKey());
            attributes.put(semantic,
                    binary.addFloatScalar(entry.getValue(), BinaryBufferBuilder.ARRAY_BUFFER));
            customSemanticMap.put(semantic, entry.getKey().toString());
        }

        LinkedHashMap<String, Object> output = new LinkedHashMap<>();
        output.put("attributes", attributes);
        output.put("indices", binary.addIndices(primitive.indices()));
        primitive.materialId().ifPresent(materialId -> output.put("material", materialIndices.get(materialId)));
        output.put("mode", 4);
        LinkedHashMap<String, Object> extras = new LinkedHashMap<>();
        extras.put("mcadPrimitiveIndex", primitiveIndex);
        if (!customSemanticMap.isEmpty()) {
            extras.put("mcadCustomAttributeSemantics", customSemanticMap);
        }
        output.put("extras", extras);
        return output;
    }

    private Object sceneExtras() {
        LinkedHashMap<String, Object> extras = GltfValueEncoder.baseExtras(
                scene.sceneId(), scene.customProperties());
        extras.put("mcadEffectiveExportTransform", GltfValueEncoder.transform(options.transform()));
        if (!scene.collisions().isEmpty()) {
            extras.put("mcadCollisions", GltfValueEncoder.neutral(scene.collisions()));
        }
        if (!scene.lights().isEmpty()) {
            extras.put("mcadLights", GltfValueEncoder.neutral(scene.lights()));
        }
        if (!scene.cameras().isEmpty()) {
            extras.put("mcadCameras", GltfValueEncoder.neutral(scene.cameras()));
        }
        if (!scene.curves().isEmpty()) {
            extras.put("mcadCurves", GltfValueEncoder.neutral(scene.curves()));
        }
        if (!scene.bones().isEmpty()) {
            extras.put("mcadBones", GltfValueEncoder.neutral(scene.bones()));
        }
        if (!scene.diagnostics().isEmpty()) {
            extras.put("mcadGenerationDiagnostics", GltfValueEncoder.neutral(scene.diagnostics()));
        }
        extras.put("mcadStatistics", GltfValueEncoder.neutral(scene.statistics()));
        return extras;
    }
}
