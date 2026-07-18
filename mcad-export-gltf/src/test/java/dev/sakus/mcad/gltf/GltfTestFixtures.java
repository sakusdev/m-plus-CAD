/*
 * SPDX-License-Identifier: MPL-2.0
 */
package dev.sakus.mcad.gltf;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.sakus.mcad.api.AlphaMode;
import dev.sakus.mcad.api.ApiVersions;
import dev.sakus.mcad.api.BoneDefinition;
import dev.sakus.mcad.api.CanonicalIdentifier;
import dev.sakus.mcad.api.Color3d;
import dev.sakus.mcad.api.Color4d;
import dev.sakus.mcad.api.CurveDefinition;
import dev.sakus.mcad.api.GeneratedScene;
import dev.sakus.mcad.api.MaterialDefinition;
import dev.sakus.mcad.api.MeshGroup;
import dev.sakus.mcad.api.MeshPrimitive;
import dev.sakus.mcad.api.MetadataValue;
import dev.sakus.mcad.api.SceneNode;
import dev.sakus.mcad.api.SceneStatistics;
import dev.sakus.mcad.api.Transform;
import dev.sakus.mcad.api.Vec3d;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;

final class GltfTestFixtures {
    private GltfTestFixtures() {
    }

    static GeneratedScene scene(
            List<MeshGroup> meshes,
            List<MaterialDefinition> materials,
            List<CurveDefinition> curves) {
        return scene(meshes, materials, curves, List.of());
    }

    static GeneratedScene scene(
            List<MeshGroup> meshes,
            List<MaterialDefinition> materials,
            List<CurveDefinition> curves,
            List<BoneDefinition> bones) {
        SceneNode root = new SceneNode(
                "node/root", "Root", Optional.empty(), Transform.IDENTITY,
                meshes.stream().map(MeshGroup::stableId).toList(), List.of(), Map.of(), List.of());
        return sceneWithNodes(meshes, materials, List.of(root), curves, bones);
    }

    static GeneratedScene sceneWithNodes(
            List<MeshGroup> meshes,
            List<MaterialDefinition> materials,
            List<SceneNode> nodes,
            List<CurveDefinition> curves) {
        return sceneWithNodes(meshes, materials, nodes, curves, List.of());
    }

    static GeneratedScene sceneWithNodes(
            List<MeshGroup> meshes,
            List<MaterialDefinition> materials,
            List<SceneNode> nodes,
            List<CurveDefinition> curves,
            List<BoneDefinition> bones) {
        List<String> roots = nodes.stream()
                .filter(node -> node.parentId().isEmpty())
                .map(SceneNode::stableId)
                .toList();
        long vertices = meshes.stream().flatMap(mesh -> mesh.primitives().stream())
                .mapToLong(primitive -> primitive.positions().size()).sum();
        long triangles = meshes.stream().flatMap(mesh -> mesh.primitives().stream())
                .mapToLong(primitive -> primitive.indices().size() / 3L).sum();
        return new GeneratedScene(
                ApiVersions.GENERATED_SCENE,
                "scene/test",
                roots,
                nodes,
                meshes,
                materials,
                List.of(),
                List.of(),
                curves,
                bones,
                List.of(),
                Map.of(new CanonicalIdentifier("test", "property"), new MetadataValue.StringValue("value")),
                List.of(),
                new SceneStatistics(0, triangles, 0, vertices, triangles, meshes.size(), materials.size()));
    }

    static MeshGroup mesh(String id, Optional<String> materialId, boolean vertexColours) {
        List<Color4d> colours = vertexColours
                ? List.of(new Color4d(1.0, 0.0, 0.0, 1.0),
                        new Color4d(0.0, 1.0, 0.0, 1.0),
                        new Color4d(0.0, 0.0, 1.0, 1.0))
                : List.of();
        MeshPrimitive primitive = new MeshPrimitive(
                List.of(new Vec3d(0.0, 0.0, 0.0),
                        new Vec3d(1.0, 0.0, 0.0),
                        new Vec3d(0.0, 1.0, 0.0)),
                List.of(new Vec3d(0.0, 0.0, 1.0),
                        new Vec3d(0.0, 0.0, 1.0),
                        new Vec3d(0.0, 0.0, 1.0)),
                List.of(0, 1, 2),
                materialId,
                colours,
                Map.of(new CanonicalIdentifier("test", "weight"), List.of(0.0, 0.5, 1.0)));
        return new MeshGroup(id, id, List.of(primitive), Map.of());
    }

    static MaterialDefinition material() {
        return new MaterialDefinition(
                "material/test",
                "Test Material",
                new Color4d(1.0, 0.25, 0.0, 0.5),
                0.2,
                0.7,
                new Color3d(0.25, 0.5, 1.0),
                2.0,
                AlphaMode.BLEND,
                OptionalDouble.empty(),
                List.of(),
                Map.of());
    }

    static MetadataValue.ListValue numbers(double... values) {
        return new MetadataValue.ListValue(Arrays.stream(values)
                .mapToObj(MetadataValue.DoubleValue::new)
                .map(MetadataValue.class::cast)
                .toList());
    }

    static String glbJson(byte[] glb) {
        ByteBuffer buffer = ByteBuffer.wrap(glb).order(ByteOrder.LITTLE_ENDIAN);
        assertEquals(0x46546c67, buffer.getInt());
        assertEquals(2, buffer.getInt());
        assertEquals(glb.length, buffer.getInt());
        int length = buffer.getInt();
        assertEquals(0x4e4f534a, buffer.getInt());
        byte[] json = new byte[length];
        buffer.get(json);
        return new String(json, StandardCharsets.UTF_8).trim();
    }

    static int occurrences(String value, String needle) {
        int count = 0;
        int offset = 0;
        while ((offset = value.indexOf(needle, offset)) >= 0) {
            count++;
            offset += needle.length();
        }
        return count;
    }

    static byte[] read(Path path) {
        try {
            return Files.readAllBytes(path);
        } catch (IOException exception) {
            throw new AssertionError(exception);
        }
    }

    static String sha256(byte[] bytes) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(bytes);
            StringBuilder result = new StringBuilder(64);
            for (byte value : digest) {
                result.append(String.format("%02x", value & 0xff));
            }
            return result.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new AssertionError(exception);
        }
    }
}
