/*
 * SPDX-License-Identifier: MPL-2.0
 */
package dev.sakus.mcad.gltf;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.sakus.mcad.api.AlphaMode;
import dev.sakus.mcad.api.ApiVersions;
import dev.sakus.mcad.api.BoneDefinition;
import dev.sakus.mcad.api.CameraDefinition;
import dev.sakus.mcad.api.CameraProjection;
import dev.sakus.mcad.api.CanonicalIdentifier;
import dev.sakus.mcad.api.Color3d;
import dev.sakus.mcad.api.Color4d;
import dev.sakus.mcad.api.CurveDefinition;
import dev.sakus.mcad.api.GeneratedScene;
import dev.sakus.mcad.api.LightDefinition;
import dev.sakus.mcad.api.LightType;
import dev.sakus.mcad.api.MaterialDefinition;
import dev.sakus.mcad.api.MeshGroup;
import dev.sakus.mcad.api.MeshPrimitive;
import dev.sakus.mcad.api.MetadataValue;
import dev.sakus.mcad.api.SceneNode;
import dev.sakus.mcad.api.SceneStatistics;
import dev.sakus.mcad.api.Transform;
import dev.sakus.mcad.api.Vec3d;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
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
import java.util.NavigableMap;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.TreeMap;

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
        return scene(meshes, materials, curves, bones, List.of(), List.of());
    }

    static GeneratedScene scene(
            List<MeshGroup> meshes,
            List<MaterialDefinition> materials,
            List<CurveDefinition> curves,
            List<BoneDefinition> bones,
            List<LightDefinition> lights,
            List<CameraDefinition> cameras) {
        SceneNode root = new SceneNode(
                "node/root", "Root", Optional.empty(), Transform.IDENTITY,
                meshes.stream().map(MeshGroup::stableId).toList(), List.of(), Map.of(), List.of());
        return sceneWithNodes(meshes, materials, List.of(root), curves, bones, lights, cameras, Transform.IDENTITY);
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
        return sceneWithNodes(meshes, materials, nodes, curves, bones, List.of(), List.of(), Transform.IDENTITY);
    }

    static GeneratedScene sceneWithNodes(
            List<MeshGroup> meshes,
            List<MaterialDefinition> materials,
            List<SceneNode> nodes,
            List<CurveDefinition> curves,
            List<BoneDefinition> bones,
            List<LightDefinition> lights,
            List<CameraDefinition> cameras,
            Transform originTransform) {
        List<String> roots = nodes.stream()
                .filter(node -> node.parentId().isEmpty())
                .map(SceneNode::stableId)
                .toList();
        long vertices = meshes.stream().flatMap(mesh -> mesh.primitives().stream())
                .mapToLong(primitive -> primitive.positions().size()).sum();
        long triangles = meshes.stream().flatMap(mesh -> mesh.primitives().stream())
                .mapToLong(primitive -> primitive.indices().size() / 3L).sum();
        SceneStatistics statistics = new SceneStatistics(
                0, triangles, 0, vertices, triangles, meshes.size(), materials.size());
        NavigableMap<CanonicalIdentifier, MetadataValue> properties = new TreeMap<>();
        properties.put(new CanonicalIdentifier("test", "property"), new MetadataValue.StringValue("value"));
        return constructGeneratedScene(
                originTransform, roots, nodes, meshes, materials, lights, cameras,
                curves, bones, properties, statistics);
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
        return new MeshGroup(id, id, List.of(primitive), Map.of(), List.of());
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
                Map.of(),
                List.of());
    }

    static CurveDefinition curve() {
        return construct(CurveDefinition.class,
                new Object[]{"curve/path", "Path", List.of(Vec3d.ZERO, Vec3d.ONE), false, List.of()},
                new Object[]{"curve/path", "Path", List.of(Vec3d.ZERO, Vec3d.ONE), false});
    }

    static BoneDefinition bone() {
        return construct(BoneDefinition.class,
                new Object[]{"bone/root", "Root Bone", Optional.empty(), Transform.IDENTITY, List.of()},
                new Object[]{"bone/root", "Root Bone", Optional.empty(), Transform.IDENTITY});
    }

    static LightDefinition light() {
        Transform transform = new Transform(new Vec3d(2.0, 3.0, 4.0),
                dev.sakus.mcad.api.Quaterniond.IDENTITY, Vec3d.ONE);
        return construct(LightDefinition.class,
                new Object[]{"light/key", "Key Light", transform, LightType.POINT,
                        new Color3d(1.0, 0.5, 0.25), 12.0,
                        OptionalDouble.of(20.0), OptionalDouble.empty(), OptionalDouble.empty(), List.of()},
                new Object[]{"light/key", "Key Light", LightType.POINT,
                        new Color3d(1.0, 0.5, 0.25), 12.0,
                        OptionalDouble.of(20.0), OptionalDouble.empty(), OptionalDouble.empty()});
    }

    static CameraDefinition camera() {
        Transform transform = new Transform(new Vec3d(0.0, 2.0, -5.0),
                dev.sakus.mcad.api.Quaterniond.IDENTITY, Vec3d.ONE);
        return construct(CameraDefinition.class,
                new Object[]{"camera/main", "Main Camera", transform, CameraProjection.PERSPECTIVE,
                        0.1, OptionalDouble.of(1000.0), OptionalDouble.of(1.0), OptionalDouble.empty(), List.of()},
                new Object[]{"camera/main", "Main Camera", CameraProjection.PERSPECTIVE,
                        0.1, OptionalDouble.of(1000.0), OptionalDouble.of(1.0), OptionalDouble.empty()});
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

    private static GeneratedScene constructGeneratedScene(
            Transform originTransform,
            List<String> roots,
            List<SceneNode> nodes,
            List<MeshGroup> meshes,
            List<MaterialDefinition> materials,
            List<LightDefinition> lights,
            List<CameraDefinition> cameras,
            List<CurveDefinition> curves,
            List<BoneDefinition> bones,
            NavigableMap<CanonicalIdentifier, MetadataValue> properties,
            SceneStatistics statistics) {
        Object[] current = {
                ApiVersions.GENERATED_SCENE, "scene/test", originTransform,
                roots, nodes, meshes, materials, lights, cameras, curves, bones, List.of(),
                properties, List.of(), statistics
        };
        Object[] legacy = {
                ApiVersions.GENERATED_SCENE, "scene/test",
                roots, nodes, meshes, materials, lights, cameras, curves, bones, List.of(),
                properties, List.of(), statistics
        };
        return construct(GeneratedScene.class, current, legacy);
    }

    private static <T> T construct(Class<T> type, Object[]... candidates) {
        ReflectiveOperationException lastFailure = null;
        for (Object[] arguments : candidates) {
            for (Constructor<?> constructor : type.getConstructors()) {
                if (constructor.getParameterCount() != arguments.length) {
                    continue;
                }
                try {
                    return type.cast(constructor.newInstance(arguments));
                } catch (IllegalArgumentException exception) {
                    // Try the overload using Map vs NavigableMap or the other contract revision.
                } catch (InstantiationException | IllegalAccessException | InvocationTargetException exception) {
                    lastFailure = exception;
                }
            }
        }
        throw new AssertionError("No compatible constructor found for " + type.getName(), lastFailure);
    }
}
