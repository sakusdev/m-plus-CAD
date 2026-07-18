/*
 * SPDX-License-Identifier: MPL-2.0
 */
package dev.sakus.mcad.export.obj;

import dev.sakus.mcad.api.AlphaMode;
import dev.sakus.mcad.api.CanonicalIdentifier;
import dev.sakus.mcad.api.Diagnostic;
import dev.sakus.mcad.api.DiagnosticSeverity;
import dev.sakus.mcad.api.GeneratedScene;
import dev.sakus.mcad.api.MaterialDefinition;
import dev.sakus.mcad.api.MeshGroup;
import dev.sakus.mcad.api.MeshPrimitive;
import dev.sakus.mcad.api.MetadataValue;
import dev.sakus.mcad.api.Vec3d;

import java.util.List;
import java.util.Map;
import java.util.Optional;

final class ObjDiagnostics {
    static final CanonicalIdentifier UNSUPPORTED_SCENE_VERSION = id("unsupported_scene_version");
    static final CanonicalIdentifier UNSUPPORTED_SCENE_ELEMENT = id("unsupported_scene_element");
    static final CanonicalIdentifier HIERARCHY_FLATTENED = id("hierarchy_flattened");
    static final CanonicalIdentifier MATERIAL_APPROXIMATED = id("material_approximated");
    static final CanonicalIdentifier EXTERNAL_ASSET_OMITTED = id("external_asset_omitted");
    static final CanonicalIdentifier NAME_SUBSTITUTED = id("name_substituted");
    static final CanonicalIdentifier ORPHAN_MESH = id("orphan_mesh_exported");
    static final CanonicalIdentifier EFFECTIVE_TRANSFORM = id("effective_transform");
    static final CanonicalIdentifier INVALID_TRANSFORM = id("invalid_transform");
    static final CanonicalIdentifier INVALID_DESTINATION = id("invalid_destination");
    static final CanonicalIdentifier IO_FAILURE = id("io_failure");
    static final CanonicalIdentifier CANCELLED = id("cancelled");
    static final CanonicalIdentifier SUCCESS = id("export_complete");

    static final CanonicalIdentifier DETAIL_FEATURE = CanonicalIdentifier.parse("mcad:feature");
    static final CanonicalIdentifier DETAIL_COUNT = CanonicalIdentifier.parse("mcad:count");
    static final CanonicalIdentifier DETAIL_REQUESTED_NAME = CanonicalIdentifier.parse("mcad:requested_name");
    static final CanonicalIdentifier DETAIL_EXPORTED_NAME = CanonicalIdentifier.parse("mcad:exported_name");
    static final CanonicalIdentifier DETAIL_PATH = CanonicalIdentifier.parse("mcad:path");
    private static final CanonicalIdentifier DETAIL_ORIGIN = CanonicalIdentifier.parse("mcad:origin_offset");
    private static final CanonicalIdentifier DETAIL_ROTATION = CanonicalIdentifier.parse("mcad:rotation_degrees");
    private static final CanonicalIdentifier DETAIL_SCALE = CanonicalIdentifier.parse("mcad:unit_scale");
    private static final CanonicalIdentifier DETAIL_AXIS = CanonicalIdentifier.parse("mcad:target_axis");

    private ObjDiagnostics() {
    }

    static void collectUnsupported(
            GeneratedScene scene,
            ObjExportSettings settings,
            List<Diagnostic> diagnostics) {
        addUnsupported(settings, diagnostics, "lights", scene.lights().size());
        addUnsupported(settings, diagnostics, "cameras", scene.cameras().size());
        addUnsupported(settings, diagnostics, "curves", scene.curves().size());
        addUnsupported(settings, diagnostics, "bones", scene.bones().size());
        addUnsupported(settings, diagnostics, "collision_metadata", scene.collisions().size());

        long vertexColourPrimitives = 0;
        long customAttributePrimitives = 0;
        for (MeshGroup mesh : scene.meshes()) {
            for (MeshPrimitive primitive : mesh.primitives()) {
                if (!primitive.vertexColours().isEmpty()) {
                    vertexColourPrimitives++;
                }
                if (!primitive.customAttributes().isEmpty()) {
                    customAttributePrimitives++;
                }
            }
        }
        addUnsupported(settings, diagnostics, "vertex_colours", vertexColourPrimitives);
        addUnsupported(settings, diagnostics, "custom_vertex_attributes", customAttributePrimitives);

        long customPropertyContainers = scene.customProperties().isEmpty() ? 0 : 1;
        customPropertyContainers += scene.nodes().stream().filter(node -> !node.customProperties().isEmpty()).count();
        customPropertyContainers += scene.meshes().stream().filter(mesh -> !mesh.customProperties().isEmpty()).count();
        addUnsupported(settings, diagnostics, "custom_properties", customPropertyContainers);

        boolean hasHierarchy = scene.nodes().stream()
                .anyMatch(node -> node.parentId().isPresent() || !node.childIds().isEmpty());
        if (hasHierarchy) {
            diagnostics.add(new Diagnostic(
                    settings.unsupportedSeverity(), HIERARCHY_FLATTENED,
                    "OBJ does not preserve node hierarchy; node transforms will be flattened into mesh instances",
                    Optional.empty(), Map.of()));
        }

        for (MaterialDefinition material : scene.materials()) {
            boolean pbrApproximation = material.metallic() != 0.0
                    || material.roughness() != 1.0
                    || material.emissiveStrength() != 0.0
                    || material.alphaMode() != AlphaMode.OPAQUE
                    || material.alphaCutoff().isPresent();
            if (pbrApproximation) {
                diagnostics.add(new Diagnostic(
                        settings.unsupportedSeverity(), MATERIAL_APPROXIMATED,
                        "MTL approximates neutral PBR properties for material: " + bounded(material.name()),
                        Optional.empty(),
                        Map.of(DETAIL_FEATURE, new MetadataValue.StringValue(material.stableId()))));
            }
            if (!material.externalUserAssetReferences().isEmpty()) {
                diagnostics.add(new Diagnostic(
                        settings.unsupportedSeverity(), EXTERNAL_ASSET_OMITTED,
                        "OBJ/MTL output does not copy or reference external user assets for material: "
                                + bounded(material.name()),
                        Optional.empty(),
                        Map.of(DETAIL_COUNT,
                                new MetadataValue.LongValue(material.externalUserAssetReferences().size()))));
            }
        }
    }

    static Diagnostic unsupported(
            ObjExportSettings settings, String feature, String message, long count) {
        return new Diagnostic(
                settings.unsupportedSeverity(), UNSUPPORTED_SCENE_ELEMENT, message, Optional.empty(),
                Map.of(
                        DETAIL_FEATURE, new MetadataValue.StringValue(feature),
                        DETAIL_COUNT, new MetadataValue.LongValue(count)));
    }

    static Diagnostic effectiveTransform(ObjExportSettings settings) {
        return new Diagnostic(
                DiagnosticSeverity.INFO, EFFECTIVE_TRANSFORM,
                "OBJ export applies origin relocation, XYZ rotation, unit scale, and target-axis conversion at the output boundary",
                Optional.empty(),
                Map.of(
                        DETAIL_ORIGIN, vector(settings.originOffset()),
                        DETAIL_ROTATION, vector(settings.rotationDegrees()),
                        DETAIL_SCALE, new MetadataValue.DoubleValue(settings.unitScale()),
                        DETAIL_AXIS, new MetadataValue.StringValue(settings.targetAxis().name())));
    }

    static Diagnostic error(
            CanonicalIdentifier code,
            String message,
            Map<CanonicalIdentifier, MetadataValue> details) {
        return new Diagnostic(DiagnosticSeverity.ERROR, code, bounded(message), Optional.empty(), details);
    }

    static String bounded(String value) {
        if (value == null || value.isBlank()) {
            return "unspecified error";
        }
        return value.length() <= 512 ? value : value.substring(0, 509) + "...";
    }

    private static void addUnsupported(
            ObjExportSettings settings,
            List<Diagnostic> diagnostics,
            String feature,
            long count) {
        if (count > 0) {
            diagnostics.add(unsupported(
                    settings, feature,
                    "OBJ cannot represent " + feature.replace('_', ' ') + " without loss", count));
        }
    }

    private static MetadataValue.ListValue vector(Vec3d vector) {
        return new MetadataValue.ListValue(List.of(
                new MetadataValue.DoubleValue(vector.x()),
                new MetadataValue.DoubleValue(vector.y()),
                new MetadataValue.DoubleValue(vector.z())));
    }

    private static CanonicalIdentifier id(String path) {
        return CanonicalIdentifier.parse("mcad:obj/" + path);
    }
}
