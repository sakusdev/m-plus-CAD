/*
 * SPDX-License-Identifier: MPL-2.0
 */
package dev.sakus.mcad.gltf;

import static dev.sakus.mcad.gltf.GltfDiagnostics.diagnostic;

import dev.sakus.mcad.api.CameraDefinition;
import dev.sakus.mcad.api.CameraProjection;
import dev.sakus.mcad.api.CanonicalIdentifier;
import dev.sakus.mcad.api.Diagnostic;
import dev.sakus.mcad.api.DiagnosticSeverity;
import dev.sakus.mcad.api.ExporterCapabilities;
import dev.sakus.mcad.api.ExportOptions;
import dev.sakus.mcad.api.GeneratedScene;
import dev.sakus.mcad.api.LightDefinition;
import dev.sakus.mcad.api.MaterialDefinition;
import dev.sakus.mcad.api.MeshGroup;
import dev.sakus.mcad.api.MeshPrimitive;
import dev.sakus.mcad.api.PreflightResult;
import dev.sakus.mcad.api.Quaterniond;
import dev.sakus.mcad.api.SceneNode;
import dev.sakus.mcad.api.Transform;
import dev.sakus.mcad.api.Vec3d;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

final class GltfPreflight {
    private static final double NORMAL_EPSILON = 1.0e-4;
    private static final double QUATERNION_EPSILON = 1.0e-8;
    private static final long MAX_BINARY_BYTES = Integer.MAX_VALUE - 64L * 1024L * 1024L;

    private GltfPreflight() {
    }

    static PreflightResult run(GeneratedScene scene, ExportOptions options, ExporterCapabilities capabilities) {
        Objects.requireNonNull(scene, "scene");
        Objects.requireNonNull(options, "options");

        List<Diagnostic> diagnostics = new ArrayList<>();
        GltfOptions.ParseResult parsed = GltfOptions.parse(options);
        diagnostics.addAll(parsed.diagnostics());
        if (!capabilities.supportsSceneVersion(scene.schemaVersion())) {
            diagnostics.add(diagnostic(DiagnosticSeverity.ERROR, "gltf/invalid-options",
                    "Unsupported GeneratedScene schema version: " + scene.schemaVersion()));
        }

        DiagnosticSeverity lossSeverity = parsed.options().lossPolicy().severity();
        reportUnsupported(diagnostics, lossSeverity, "curves", scene.curves());
        reportUnsupported(diagnostics, lossSeverity, "bones", scene.bones());

        for (MaterialDefinition material : scene.materials()) {
            if (!material.externalUserAssetReferences().isEmpty()) {
                diagnostics.add(diagnostic(lossSeverity, "gltf/user-asset-reference",
                        "Material '" + material.stableId()
                                + "' contains user asset references; references are preserved in extras but textures are not copied or linked."));
            }
        }

        checkTransform(diagnostics, SceneContractAdapter.originTransform(scene), "scene origin transform");
        checkTransform(diagnostics, parsed.options().transform(), "configured export transform");

        long estimatedBytes = 0L;
        Set<String> referencedMeshes = new HashSet<>();
        for (SceneNode node : scene.nodes()) {
            referencedMeshes.addAll(node.meshIds());
            checkTransform(diagnostics, node.localTransform(), "node '" + node.stableId() + "'");
        }
        for (LightDefinition light : scene.lights()) {
            checkTransform(diagnostics, SceneContractAdapter.transform(light), "light '" + light.stableId() + "'");
        }
        for (CameraDefinition camera : scene.cameras()) {
            checkTransform(diagnostics, SceneContractAdapter.transform(camera), "camera '" + camera.stableId() + "'");
            if (camera.projection() == CameraProjection.ORTHOGRAPHIC && camera.farPlane().isEmpty()) {
                diagnostics.add(diagnostic(lossSeverity, "gltf/unsupported-camera",
                        "Orthographic camera '" + camera.stableId()
                                + "' has no far plane; core glTF requires zfar, so the neutral camera record will be preserved in scene extras."));
            }
        }

        for (MeshGroup mesh : scene.meshes()) {
            if (!referencedMeshes.contains(mesh.stableId())) {
                diagnostics.add(diagnostic(DiagnosticSeverity.INFO, "gltf/unattached-mesh-preserved",
                        "Unattached mesh '" + mesh.stableId() + "' will be preserved as a synthetic root node."));
            }
            for (MeshPrimitive primitive : mesh.primitives()) {
                estimatedBytes = addEstimatedBytes(estimatedBytes, primitive.positions().size(), 3L * Float.BYTES);
                estimatedBytes = addEstimatedBytes(estimatedBytes, primitive.normals().size(), 3L * Float.BYTES);
                estimatedBytes = addEstimatedBytes(estimatedBytes, primitive.indices().size(), Integer.BYTES);
                estimatedBytes = addEstimatedBytes(estimatedBytes, primitive.vertexColours().size(), 4L * Float.BYTES);
                for (List<Double> values : primitive.customAttributes().values()) {
                    estimatedBytes = addEstimatedBytes(estimatedBytes, values.size(), Float.BYTES);
                }
                checkCustomAttributes(diagnostics, primitive);
                checkGeometry(diagnostics, primitive);
            }
        }
        if (estimatedBytes > MAX_BINARY_BYTES) {
            diagnostics.add(diagnostic(DiagnosticSeverity.ERROR, "gltf/size-limit",
                    "Estimated binary buffer exceeds the in-memory exporter limit of "
                            + MAX_BINARY_BYTES + " bytes."));
        }
        return new PreflightResult(diagnostics);
    }

    private static void checkCustomAttributes(List<Diagnostic> diagnostics, MeshPrimitive primitive) {
        Map<String, CanonicalIdentifier> semantics = new HashMap<>();
        for (CanonicalIdentifier attribute : primitive.customAttributes().keySet()) {
            String semantic = GltfNames.attributeSemantic(attribute);
            CanonicalIdentifier previous = semantics.putIfAbsent(semantic, attribute);
            if (previous != null) {
                diagnostics.add(diagnostic(DiagnosticSeverity.ERROR, "gltf/attribute-semantic-collision",
                        "Custom attributes '" + previous + "' and '" + attribute
                                + "' map to the same glTF semantic '" + semantic + "'."));
            }
        }
    }

    private static void checkGeometry(List<Diagnostic> diagnostics, MeshPrimitive primitive) {
        for (Vec3d position : primitive.positions()) {
            checkFloatRange(diagnostics, position.x(), "position.x");
            checkFloatRange(diagnostics, position.y(), "position.y");
            checkFloatRange(diagnostics, position.z(), "position.z");
        }
        for (Vec3d normal : primitive.normals()) {
            checkFloatRange(diagnostics, normal.x(), "normal.x");
            checkFloatRange(diagnostics, normal.y(), "normal.y");
            checkFloatRange(diagnostics, normal.z(), "normal.z");
            double magnitude = magnitude(normal.x(), normal.y(), normal.z());
            if (magnitude <= NORMAL_EPSILON) {
                diagnostics.add(diagnostic(DiagnosticSeverity.ERROR, "gltf/invalid-normal",
                        "glTF normals must be non-zero."));
            } else if (Math.abs(magnitude - 1.0) > NORMAL_EPSILON) {
                diagnostics.add(diagnostic(DiagnosticSeverity.WARNING, "gltf/normal-normalized",
                        "A non-unit normal will be normalized for glTF."));
            }
        }
        for (List<Double> values : primitive.customAttributes().values()) {
            for (double value : values) {
                checkFloatRange(diagnostics, value, "custom attribute");
            }
        }
    }

    private static void reportUnsupported(
            List<Diagnostic> diagnostics,
            DiagnosticSeverity severity,
            String type,
            Collection<?> values) {
        if (!values.isEmpty()) {
            diagnostics.add(diagnostic(severity, "gltf/unsupported-scene-element",
                    "Scene contains " + values.size() + " " + type
                            + "; standard glTF semantics are not available, so neutral records will be preserved in scene extras."));
        }
    }

    private static long addEstimatedBytes(long current, int count, long bytesPerElement) {
        try {
            return Math.addExact(current, Math.multiplyExact((long) count, bytesPerElement));
        } catch (ArithmeticException exception) {
            return Long.MAX_VALUE;
        }
    }

    private static void checkTransform(
            List<Diagnostic> diagnostics,
            Transform transform,
            String field) {
        checkFloatRange(diagnostics, transform.translation().x(), field + " translation.x");
        checkFloatRange(diagnostics, transform.translation().y(), field + " translation.y");
        checkFloatRange(diagnostics, transform.translation().z(), field + " translation.z");
        checkFloatRange(diagnostics, transform.scale().x(), field + " scale.x");
        checkFloatRange(diagnostics, transform.scale().y(), field + " scale.y");
        checkFloatRange(diagnostics, transform.scale().z(), field + " scale.z");
        if (!isUnitQuaternion(transform.rotation())) {
            diagnostics.add(diagnostic(DiagnosticSeverity.WARNING, "gltf/quaternion-normalized",
                    field + " rotation will be normalized for glTF."));
        }
    }

    private static void checkFloatRange(List<Diagnostic> diagnostics, double value, String field) {
        if (!Float.isFinite((float) value)) {
            diagnostics.add(diagnostic(DiagnosticSeverity.ERROR, "gltf/float-range",
                    field + " is outside the finite IEEE-754 float range required by glTF."));
        }
    }

    private static boolean isUnitQuaternion(Quaterniond value) {
        double magnitude = Math.sqrt(value.x() * value.x() + value.y() * value.y()
                + value.z() * value.z() + value.w() * value.w());
        return Math.abs(magnitude - 1.0) <= QUATERNION_EPSILON;
    }

    static double magnitude(double x, double y, double z) {
        return Math.sqrt(x * x + y * y + z * z);
    }
}
