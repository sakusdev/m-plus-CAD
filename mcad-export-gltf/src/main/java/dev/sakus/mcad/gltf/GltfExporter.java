/*
 * SPDX-License-Identifier: MPL-2.0
 */
package dev.sakus.mcad.gltf;

import static dev.sakus.mcad.gltf.GltfDiagnostics.id;

import dev.sakus.mcad.api.ApiVersions;
import dev.sakus.mcad.api.CancellationToken;
import dev.sakus.mcad.api.CanonicalIdentifier;
import dev.sakus.mcad.api.ExportOptions;
import dev.sakus.mcad.api.ExportResult;
import dev.sakus.mcad.api.ExporterCapabilities;
import dev.sakus.mcad.api.ExporterFeature;
import dev.sakus.mcad.api.ExporterLimits;
import dev.sakus.mcad.api.GeneratedScene;
import dev.sakus.mcad.api.ModelExporter;
import dev.sakus.mcad.api.PreflightResult;
import dev.sakus.mcad.api.ProgressReporter;

import java.io.IOException;
import java.nio.file.Path;
import java.util.EnumSet;
import java.util.NavigableSet;
import java.util.Objects;
import java.util.Set;

/** Deterministic glTF 2.0 / GLB exporter for neutral m+CAD scenes. */
public final class GltfExporter implements ModelExporter {
    private static final CanonicalIdentifier FORMAT_ID = id("gltf");
    private static final ExporterCapabilities CAPABILITIES = new ExporterCapabilities(
            ApiVersions.GENERATED_SCENE,
            ApiVersions.GENERATED_SCENE,
            EnumSet.of(
                    ExporterFeature.HIERARCHY,
                    ExporterFeature.MULTIPLE_MESHES,
                    ExporterFeature.MULTIPLE_MATERIALS,
                    ExporterFeature.VERTEX_COLOURS,
                    ExporterFeature.ALPHA_MODES,
                    ExporterFeature.CUSTOM_PROPERTIES,
                    ExporterFeature.LIGHTS,
                    ExporterFeature.CAMERAS,
                    ExporterFeature.COLLISION_METADATA,
                    ExporterFeature.EMBEDDED_BINARY_ASSETS,
                    ExporterFeature.EXTERNAL_FILE_SETS),
            ExporterLimits.unlimited(),
            Set.of("glb", "gltf"));

    @Override
    public CanonicalIdentifier formatId() {
        return FORMAT_ID;
    }

    @Override
    public String displayName() {
        return "glTF 2.0 / GLB";
    }

    @Override
    public NavigableSet<String> fileExtensions() {
        return CAPABILITIES.fileExtensions();
    }

    @Override
    public ExporterCapabilities capabilities() {
        return CAPABILITIES;
    }

    @Override
    public PreflightResult preflight(GeneratedScene scene, ExportOptions options) {
        return GltfPreflight.run(scene, options, CAPABILITIES);
    }

    @Override
    public ExportResult export(
            GeneratedScene scene,
            Path destination,
            ExportOptions options,
            ProgressReporter progress,
            CancellationToken cancellation) throws IOException {
        Objects.requireNonNull(scene, "scene");
        Objects.requireNonNull(destination, "destination");
        Objects.requireNonNull(options, "options");
        Objects.requireNonNull(progress, "progress");
        Objects.requireNonNull(cancellation, "cancellation");
        return GltfExportOperation.run(scene, destination, options, progress, cancellation, CAPABILITIES);
    }
}
