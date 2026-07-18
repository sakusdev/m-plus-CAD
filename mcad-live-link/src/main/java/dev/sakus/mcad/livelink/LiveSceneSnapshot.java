/*
 * SPDX-License-Identifier: MPL-2.0
 */
package dev.sakus.mcad.livelink;

import dev.sakus.mcad.api.BoneDefinition;
import dev.sakus.mcad.api.CameraDefinition;
import dev.sakus.mcad.api.CollisionDefinition;
import dev.sakus.mcad.api.Color3d;
import dev.sakus.mcad.api.Color4d;
import dev.sakus.mcad.api.CurveDefinition;
import dev.sakus.mcad.api.GeneratedScene;
import dev.sakus.mcad.api.LightDefinition;
import dev.sakus.mcad.api.MaterialDefinition;
import dev.sakus.mcad.api.MeshGroup;
import dev.sakus.mcad.api.MeshPrimitive;
import dev.sakus.mcad.api.Quaterniond;
import dev.sakus.mcad.api.SceneNode;
import dev.sakus.mcad.api.Transform;
import dev.sakus.mcad.api.Vec3d;

import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.NavigableMap;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.TreeMap;
import java.util.function.Function;

/** Deterministic wire-ready representation of one immutable generated scene. */
public record LiveSceneSnapshot(
        String sceneId,
        String sceneMetadataJson,
        NavigableMap<String, String> materials,
        NavigableMap<String, String> meshes,
        NavigableMap<String, String> nodes,
        NavigableMap<String, String> lights,
        NavigableMap<String, String> cameras,
        NavigableMap<String, String> curves,
        NavigableMap<String, String> bones,
        NavigableMap<String, String> collisions) {

    public LiveSceneSnapshot {
        Objects.requireNonNull(sceneId, "sceneId");
        Objects.requireNonNull(sceneMetadataJson, "sceneMetadataJson");
        materials = immutable(materials, "materials");
        meshes = immutable(meshes, "meshes");
        nodes = immutable(nodes, "nodes");
        lights = immutable(lights, "lights");
        cameras = immutable(cameras, "cameras");
        curves = immutable(curves, "curves");
        bones = immutable(bones, "bones");
        collisions = immutable(collisions, "collisions");
    }

    public static LiveSceneSnapshot capture(GeneratedScene scene) {
        return capture(scene, Transform.IDENTITY);
    }

    public static LiveSceneSnapshot capture(GeneratedScene scene, Transform displayTransform) {
        Objects.requireNonNull(scene, "scene");
        Objects.requireNonNull(displayTransform, "displayTransform");
        Transform effectiveRoot = compose(displayTransform, scene.originTransform());
        String metadata = "{"
                + "\"display\":" + transform(displayTransform) + ','
                + "\"sourceOrigin\":" + transform(scene.originTransform()) + ','
                + "\"origin\":" + transform(effectiveRoot) + ','
                + "\"roots\":" + strings(scene.rootNodeIds()) + ','
                + "\"schemaVersion\":" + LiveLinkJson.quote(scene.schemaVersion().toString()) + ','
                + "\"statistics\":{"
                + "\"sourceBlockCount\":" + scene.statistics().sourceBlockCount() + ','
                + "\"visibleFaceCount\":" + scene.statistics().visibleFaceCount() + ','
                + "\"removedHiddenFaceCount\":" + scene.statistics().removedHiddenFaceCount() + ','
                + "\"vertexCount\":" + scene.statistics().vertexCount() + ','
                + "\"triangleCount\":" + scene.statistics().triangleCount() + ','
                + "\"meshCount\":" + scene.statistics().meshCount() + ','
                + "\"materialCount\":" + scene.statistics().materialCount()
                + "}}";
        return new LiveSceneSnapshot(
                scene.sceneId(),
                metadata,
                indexed(scene.materials(), MaterialDefinition::stableId, LiveSceneSnapshot::material),
                indexed(scene.meshes(), MeshGroup::stableId, LiveSceneSnapshot::mesh),
                indexed(scene.nodes(), SceneNode::stableId, LiveSceneSnapshot::node),
                indexed(scene.lights(), LightDefinition::stableId, LiveSceneSnapshot::light),
                indexed(scene.cameras(), CameraDefinition::stableId, LiveSceneSnapshot::camera),
                indexed(scene.curves(), CurveDefinition::stableId, LiveSceneSnapshot::curve),
                indexed(scene.bones(), BoneDefinition::stableId, LiveSceneSnapshot::bone),
                indexed(scene.collisions(), CollisionDefinition::stableId, LiveSceneSnapshot::collision));
    }

    private static String material(MaterialDefinition value) {
        return "{"
                + "\"id\":" + LiveLinkJson.quote(value.stableId()) + ','
                + "\"name\":" + LiveLinkJson.quote(value.name()) + ','
                + "\"baseColor\":" + colour(value.baseColour()) + ','
                + "\"metallic\":" + LiveLinkJson.number(value.metallic()) + ','
                + "\"roughness\":" + LiveLinkJson.number(value.roughness()) + ','
                + "\"emissiveColor\":" + colour(value.emissiveColour()) + ','
                + "\"emissiveStrength\":" + LiveLinkJson.number(value.emissiveStrength()) + ','
                + "\"alphaMode\":" + LiveLinkJson.quote(value.alphaMode().name().toLowerCase(Locale.ROOT)) + ','
                + "\"alphaCutoff\":" + optional(value.alphaCutoff())
                + '}';
    }

    private static String mesh(MeshGroup value) {
        return "{"
                + "\"id\":" + LiveLinkJson.quote(value.stableId()) + ','
                + "\"name\":" + LiveLinkJson.quote(value.name()) + ','
                + "\"primitives\":" + LiveLinkJson.array(value.primitives(), LiveSceneSnapshot::primitive)
                + '}';
    }

    private static String primitive(MeshPrimitive value) {
        return "{"
                + "\"positions\":" + LiveLinkJson.array(value.positions(), LiveSceneSnapshot::vector) + ','
                + "\"normals\":" + LiveLinkJson.array(value.normals(), LiveSceneSnapshot::vector) + ','
                + "\"indices\":" + LiveLinkJson.array(value.indices(), index -> Integer.toString(index)) + ','
                + "\"materialId\":" + nullable(value.materialId()) + ','
                + "\"colors\":" + LiveLinkJson.array(value.vertexColours(), LiveSceneSnapshot::colour)
                + '}';
    }

    private static String node(SceneNode value) {
        return "{"
                + "\"id\":" + LiveLinkJson.quote(value.stableId()) + ','
                + "\"name\":" + LiveLinkJson.quote(value.name()) + ','
                + "\"parentId\":" + nullable(value.parentId()) + ','
                + "\"transform\":" + transform(value.localTransform()) + ','
                + "\"meshIds\":" + strings(value.meshIds()) + ','
                + "\"childIds\":" + strings(value.childIds())
                + '}';
    }

    private static String light(LightDefinition value) {
        return "{"
                + "\"id\":" + LiveLinkJson.quote(value.stableId()) + ','
                + "\"name\":" + LiveLinkJson.quote(value.name()) + ','
                + "\"transform\":" + transform(value.transform()) + ','
                + "\"lightType\":" + LiveLinkJson.quote(value.type().name().toLowerCase(Locale.ROOT)) + ','
                + "\"color\":" + colour(value.colour()) + ','
                + "\"intensity\":" + LiveLinkJson.number(value.intensity()) + ','
                + "\"range\":" + optional(value.range()) + ','
                + "\"innerCone\":" + optional(value.innerConeRadians()) + ','
                + "\"outerCone\":" + optional(value.outerConeRadians())
                + '}';
    }

    private static String camera(CameraDefinition value) {
        return "{"
                + "\"id\":" + LiveLinkJson.quote(value.stableId()) + ','
                + "\"name\":" + LiveLinkJson.quote(value.name()) + ','
                + "\"transform\":" + transform(value.transform()) + ','
                + "\"projection\":" + LiveLinkJson.quote(value.projection().name().toLowerCase(Locale.ROOT)) + ','
                + "\"nearPlane\":" + LiveLinkJson.number(value.nearPlane()) + ','
                + "\"farPlane\":" + optional(value.farPlane()) + ','
                + "\"verticalFov\":" + optional(value.verticalFieldOfViewRadians()) + ','
                + "\"orthographicHeight\":" + optional(value.orthographicHeight())
                + '}';
    }

    private static String curve(CurveDefinition value) {
        return "{"
                + "\"id\":" + LiveLinkJson.quote(value.stableId()) + ','
                + "\"name\":" + LiveLinkJson.quote(value.name()) + ','
                + "\"points\":" + LiveLinkJson.array(value.controlPoints(), LiveSceneSnapshot::vector) + ','
                + "\"closed\":" + value.closed()
                + '}';
    }

    private static String bone(BoneDefinition value) {
        return "{"
                + "\"id\":" + LiveLinkJson.quote(value.stableId()) + ','
                + "\"name\":" + LiveLinkJson.quote(value.name()) + ','
                + "\"parentId\":" + nullable(value.parentId()) + ','
                + "\"transform\":" + transform(value.localTransform())
                + '}';
    }

    private static String collision(CollisionDefinition value) {
        return "{"
                + "\"id\":" + LiveLinkJson.quote(value.stableId()) + ','
                + "\"name\":" + LiveLinkJson.quote(value.name()) + ','
                + "\"kind\":" + LiveLinkJson.quote(value.kind().name().toLowerCase(Locale.ROOT)) + ','
                + "\"meshIds\":" + strings(value.meshIds())
                + '}';
    }

    private static Transform compose(Transform outer, Transform inner) {
        Vec3d scaledTranslation = componentMultiply(outer.scale(), inner.translation());
        Vec3d rotatedTranslation = rotate(outer.rotation(), scaledTranslation);
        Vec3d translation = add(outer.translation(), rotatedTranslation);
        Quaterniond rotation = multiply(outer.rotation(), inner.rotation());
        Vec3d scale = componentMultiply(outer.scale(), inner.scale());
        return new Transform(translation, rotation, scale);
    }

    private static Vec3d rotate(Quaterniond quaternion, Vec3d value) {
        double magnitude = Math.sqrt(
                quaternion.x() * quaternion.x()
                        + quaternion.y() * quaternion.y()
                        + quaternion.z() * quaternion.z()
                        + quaternion.w() * quaternion.w());
        double x = quaternion.x() / magnitude;
        double y = quaternion.y() / magnitude;
        double z = quaternion.z() / magnitude;
        double w = quaternion.w() / magnitude;
        Vec3d t = new Vec3d(
                2.0 * (y * value.z() - z * value.y()),
                2.0 * (z * value.x() - x * value.z()),
                2.0 * (x * value.y() - y * value.x()));
        Vec3d cross = new Vec3d(
                y * t.z() - z * t.y(),
                z * t.x() - x * t.z(),
                x * t.y() - y * t.x());
        return new Vec3d(
                value.x() + w * t.x() + cross.x(),
                value.y() + w * t.y() + cross.y(),
                value.z() + w * t.z() + cross.z());
    }

    private static Quaterniond multiply(Quaterniond left, Quaterniond right) {
        return new Quaterniond(
                left.w() * right.x() + left.x() * right.w()
                        + left.y() * right.z() - left.z() * right.y(),
                left.w() * right.y() - left.x() * right.z()
                        + left.y() * right.w() + left.z() * right.x(),
                left.w() * right.z() + left.x() * right.y()
                        - left.y() * right.x() + left.z() * right.w(),
                left.w() * right.w() - left.x() * right.x()
                        - left.y() * right.y() - left.z() * right.z());
    }

    private static Vec3d componentMultiply(Vec3d left, Vec3d right) {
        return new Vec3d(left.x() * right.x(), left.y() * right.y(), left.z() * right.z());
    }

    private static Vec3d add(Vec3d left, Vec3d right) {
        return new Vec3d(left.x() + right.x(), left.y() + right.y(), left.z() + right.z());
    }

    private static String transform(Transform value) {
        return "{"
                + "\"translation\":" + vector(value.translation()) + ','
                + "\"rotation\":" + quaternion(value.rotation()) + ','
                + "\"scale\":" + vector(value.scale())
                + '}';
    }

    private static String vector(Vec3d value) {
        return '[' + LiveLinkJson.number(value.x()) + ','
                + LiveLinkJson.number(value.y()) + ','
                + LiveLinkJson.number(value.z()) + ']';
    }

    private static String quaternion(Quaterniond value) {
        return '[' + LiveLinkJson.number(value.x()) + ','
                + LiveLinkJson.number(value.y()) + ','
                + LiveLinkJson.number(value.z()) + ','
                + LiveLinkJson.number(value.w()) + ']';
    }

    private static String colour(Color3d value) {
        return '[' + LiveLinkJson.number(value.red()) + ','
                + LiveLinkJson.number(value.green()) + ','
                + LiveLinkJson.number(value.blue()) + ']';
    }

    private static String colour(Color4d value) {
        return '[' + LiveLinkJson.number(value.red()) + ','
                + LiveLinkJson.number(value.green()) + ','
                + LiveLinkJson.number(value.blue()) + ','
                + LiveLinkJson.number(value.alpha()) + ']';
    }

    private static String strings(List<String> values) {
        return LiveLinkJson.array(values, LiveLinkJson::quote);
    }

    private static String nullable(Optional<String> value) {
        return value.map(LiveLinkJson::quote).orElse("null");
    }

    private static String optional(OptionalDouble value) {
        return value.isPresent() ? LiveLinkJson.number(value.getAsDouble()) : "null";
    }

    private static <T> NavigableMap<String, String> indexed(
            List<T> values,
            Function<T, String> id,
            Function<T, String> encoder) {
        TreeMap<String, String> result = new TreeMap<>();
        for (T value : values) {
            String stableId = Objects.requireNonNull(id.apply(value), "stableId");
            if (result.put(stableId, Objects.requireNonNull(encoder.apply(value), "encoded value")) != null) {
                throw new IllegalArgumentException("duplicate stableId: " + stableId);
            }
        }
        return Collections.unmodifiableNavigableMap(result);
    }

    private static NavigableMap<String, String> immutable(NavigableMap<String, String> values, String name) {
        Objects.requireNonNull(values, name);
        TreeMap<String, String> copy = new TreeMap<>();
        for (var entry : values.entrySet()) {
            copy.put(
                    Objects.requireNonNull(entry.getKey(), name + " key"),
                    Objects.requireNonNull(entry.getValue(), name + " value"));
        }
        return Collections.unmodifiableNavigableMap(copy);
    }
}
