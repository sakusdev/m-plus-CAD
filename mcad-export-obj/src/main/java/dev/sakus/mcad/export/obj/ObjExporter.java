/*
 * SPDX-License-Identifier: MPL-2.0
 */
package dev.sakus.mcad.export.obj;

import dev.sakus.mcad.api.ApiVersions;
import dev.sakus.mcad.api.CancellationToken;
import dev.sakus.mcad.api.CanonicalIdentifier;
import dev.sakus.mcad.api.Diagnostic;
import dev.sakus.mcad.api.DiagnosticSeverity;
import dev.sakus.mcad.api.ExportOptions;
import dev.sakus.mcad.api.ExportResult;
import dev.sakus.mcad.api.ExportStatus;
import dev.sakus.mcad.api.ExporterCapabilities;
import dev.sakus.mcad.api.ExporterFeature;
import dev.sakus.mcad.api.ExporterLimits;
import dev.sakus.mcad.api.GeneratedScene;
import dev.sakus.mcad.api.MetadataValue;
import dev.sakus.mcad.api.ModelExporter;
import dev.sakus.mcad.api.PreflightResult;
import dev.sakus.mcad.api.ProducedFile;
import dev.sakus.mcad.api.ProgressReporter;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.NavigableSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CancellationException;

/** Deterministic Wavefront OBJ and MTL exporter for neutral {@link GeneratedScene} values. */
public final class ObjExporter implements ModelExporter {
    private static final CanonicalIdentifier FORMAT_ID = CanonicalIdentifier.parse("mcad:obj");
    private static final ExporterCapabilities CAPABILITIES = new ExporterCapabilities(
            ApiVersions.GENERATED_SCENE,
            ApiVersions.GENERATED_SCENE,
            Set.of(
                    ExporterFeature.MULTIPLE_MESHES,
                    ExporterFeature.MULTIPLE_MATERIALS,
                    ExporterFeature.EXTERNAL_FILE_SETS),
            ExporterLimits.unlimited(),
            Set.of("obj"));

    @Override
    public CanonicalIdentifier formatId() {
        return FORMAT_ID;
    }

    @Override
    public String displayName() {
        return "Wavefront OBJ / MTL";
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
        Objects.requireNonNull(scene, "scene");
        Objects.requireNonNull(options, "options");

        List<Diagnostic> diagnostics = new ArrayList<>();
        ObjExportSettings.ParseResult parsed = ObjExportSettings.parse(options);
        diagnostics.addAll(parsed.diagnostics());
        if (!CAPABILITIES.supportsSceneVersion(scene.schemaVersion())) {
            diagnostics.add(new Diagnostic(
                    DiagnosticSeverity.ERROR,
                    ObjDiagnostics.UNSUPPORTED_SCENE_VERSION,
                    "OBJ exporter supports generated-scene schema " + ApiVersions.GENERATED_SCENE
                            + " but received " + scene.schemaVersion(),
                    Optional.empty(), Map.of()));
        }
        if (parsed.settings().isEmpty()) {
            return new PreflightResult(diagnostics);
        }

        ObjExportSettings settings = parsed.settings().orElseThrow();
        ObjDiagnostics.collectUnsupported(scene, settings, diagnostics);
        ObjExportPlan plan;
        try {
            plan = ObjExportPlan.build(scene, settings, diagnostics);
        } catch (IllegalArgumentException | ArithmeticException exception) {
            diagnostics.add(ObjDiagnostics.error(
                    ObjDiagnostics.INVALID_TRANSFORM,
                    "Scene or export transform cannot be represented safely: "
                            + ObjDiagnostics.bounded(exception.getMessage()),
                    Map.of()));
            return new PreflightResult(diagnostics);
        }
        diagnostics.add(ObjDiagnostics.effectiveTransform(settings));
        if (plan.instances().isEmpty()) {
            diagnostics.add(ObjDiagnostics.unsupported(
                    settings, "empty_scene",
                    "The scene contains no mesh groups; OBJ output will contain headers only", 0));
        }
        return new PreflightResult(diagnostics);
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

        PreflightResult preflight = preflight(scene, options);
        if (!preflight.canExport()) {
            return new ExportResult(ExportStatus.FAILED, List.of(), preflight.diagnostics());
        }
        ObjExportSettings settings = ObjExportSettings.parse(options).settings().orElseThrow();
        List<Diagnostic> diagnostics = new ArrayList<>(preflight.diagnostics());
        ObjExportPlan plan = ObjExportPlan.build(scene, settings, new ArrayList<>());

        ObjFileTransaction.DestinationPair outputs;
        try {
            outputs = ObjFileTransaction.destination(destination);
        } catch (IllegalArgumentException exception) {
            diagnostics.add(ObjDiagnostics.error(
                    ObjDiagnostics.INVALID_DESTINATION,
                    exception.getMessage(),
                    Map.of(ObjDiagnostics.DETAIL_PATH,
                            new MetadataValue.StringValue(ObjDiagnostics.bounded(destination.toString())))));
            return new ExportResult(ExportStatus.FAILED, List.of(), diagnostics);
        }

        Path objTemporary = null;
        Path mtlTemporary = null;
        try {
            cancellation.throwIfCancellationRequested();
            Path parent = outputs.obj().getParent();
            objTemporary = Files.createTempFile(parent, ".mcad-obj-", ".tmp");
            mtlTemporary = Files.createTempFile(parent, ".mcad-mtl-", ".tmp");

            ObjOutputWriter.ProgressTracker tracker = ObjOutputWriter.progress(progress, plan.workUnits());
            tracker.report("Preparing deterministic OBJ/MTL output");
            ObjOutputWriter.writeMtl(mtlTemporary, plan, cancellation, tracker);
            ObjOutputWriter.writeObj(
                    objTemporary, outputs.mtl().getFileName().toString(), plan, cancellation, tracker);
            cancellation.throwIfCancellationRequested();

            ObjFileTransaction.commit(objTemporary, outputs.obj(), mtlTemporary, outputs.mtl());
            objTemporary = null;
            mtlTemporary = null;
            tracker.finish();

            List<ProducedFile> producedFiles = List.of(
                    ObjFileTransaction.producedFile(outputs.obj(), "model/obj"),
                    ObjFileTransaction.producedFile(outputs.mtl(), "text/plain"));
            diagnostics.add(new Diagnostic(
                    DiagnosticSeverity.INFO, ObjDiagnostics.SUCCESS,
                    "Exported deterministic OBJ and MTL files", Optional.empty(),
                    Map.of(ObjDiagnostics.DETAIL_COUNT,
                            new MetadataValue.LongValue(producedFiles.size()))));
            return new ExportResult(ExportStatus.SUCCESS, producedFiles, diagnostics);
        } catch (CancellationException exception) {
            diagnostics.add(new Diagnostic(
                    DiagnosticSeverity.INFO, ObjDiagnostics.CANCELLED,
                    "OBJ export was cancelled before final files were published",
                    Optional.empty(), Map.of()));
            return new ExportResult(ExportStatus.CANCELLED, List.of(), diagnostics);
        } catch (IOException exception) {
            diagnostics.add(ObjDiagnostics.error(
                    ObjDiagnostics.IO_FAILURE,
                    "OBJ export failed: " + ObjDiagnostics.bounded(exception.getMessage()),
                    Map.of(ObjDiagnostics.DETAIL_PATH,
                            new MetadataValue.StringValue(ObjDiagnostics.bounded(destination.toString())))));
            return new ExportResult(ExportStatus.FAILED, List.of(), diagnostics);
        } finally {
            ObjFileTransaction.deleteQuietly(objTemporary);
            ObjFileTransaction.deleteQuietly(mtlTemporary);
        }
    }
}
