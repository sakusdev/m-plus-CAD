/*
 * SPDX-License-Identifier: MPL-2.0
 */
package dev.sakus.mcad.minecraft.runtime;

import dev.sakus.mcad.api.CancellationToken;
import dev.sakus.mcad.api.CanonicalIdentifier;
import dev.sakus.mcad.api.ExportOptions;
import dev.sakus.mcad.api.ExportResult;
import dev.sakus.mcad.api.GeneratedScene;
import dev.sakus.mcad.api.MetadataValue;
import dev.sakus.mcad.api.ModelExporter;
import dev.sakus.mcad.api.ProgressReporter;
import dev.sakus.mcad.api.ProjectSettings;
import dev.sakus.mcad.api.Quaterniond;
import dev.sakus.mcad.api.StructureSnapshot;
import dev.sakus.mcad.api.Vec3d;
import dev.sakus.mcad.core.mesh.FullCubeMeshGenerator;
import dev.sakus.mcad.markers.ConfigurableMarkerInterpreter;
import dev.sakus.mcad.markers.MarkerInterpretationResult;
import dev.sakus.mcad.markers.MarkerRuleSet;
import dev.sakus.mcad.materials.MaterialResolver;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Complete loader-independent processing path used by the Fabric runtime and end-to-end tests. */
public final class MvpPipeline {
    public record Output(
            StructureSnapshot processedSnapshot,
            MarkerInterpretationResult markerResult,
            GeneratedScene scene,
            ExportResult exportResult) {
        public Output {
            Objects.requireNonNull(processedSnapshot, "processedSnapshot");
            Objects.requireNonNull(markerResult, "markerResult");
            Objects.requireNonNull(scene, "scene");
            Objects.requireNonNull(exportResult, "exportResult");
        }
    }

    private final ConfigurableMarkerInterpreter markerInterpreter;
    private final FullCubeMeshGenerator meshGenerator;
    private final MaterialResolver materialResolver;

    public MvpPipeline() {
        this(new ConfigurableMarkerInterpreter(), new FullCubeMeshGenerator(), new MaterialResolver());
    }

    public MvpPipeline(
            ConfigurableMarkerInterpreter markerInterpreter,
            FullCubeMeshGenerator meshGenerator,
            MaterialResolver materialResolver) {
        this.markerInterpreter = Objects.requireNonNull(markerInterpreter, "markerInterpreter");
        this.meshGenerator = Objects.requireNonNull(meshGenerator, "meshGenerator");
        this.materialResolver = Objects.requireNonNull(materialResolver, "materialResolver");
    }

    public Output run(
            StructureSnapshot snapshot,
            ProjectSettings settings,
            MarkerRuleSet markerRules,
            ModelExporter exporter,
            Path destination,
            ProgressReporter progress,
            CancellationToken cancellation) throws IOException {
        Objects.requireNonNull(snapshot, "snapshot");
        Objects.requireNonNull(settings, "settings");
        Objects.requireNonNull(markerRules, "markerRules");
        Objects.requireNonNull(exporter, "exporter");
        Objects.requireNonNull(destination, "destination");
        Objects.requireNonNull(progress, "progress");
        Objects.requireNonNull(cancellation, "cancellation");

        cancellation.throwIfCancellationRequested();
        MarkerInterpretationResult markerResult = settings.markers().enabled()
                ? markerInterpreter.interpret(snapshot, markerRules, cancellation, progress)
                : new MarkerInterpretationResult(
                        snapshot.blocks(), List.of(), List.of(), List.of(), List.of());

        StructureSnapshot processed = markerResult.consumedPositions().isEmpty()
                ? snapshot
                : new StructureSnapshot(
                        snapshot.schemaVersion(),
                        snapshot.snapshotId() + "/markers-v1",
                        snapshot.size(),
                        snapshot.sourceWorldOrigin(),
                        markerResult.retainedBlocks(),
                        snapshot.metadata());

        cancellation.throwIfCancellationRequested();
        GeneratedScene generated = meshGenerator.generate(processed, settings, cancellation, progress);
        GeneratedScene scene = SceneAssembler.assemble(generated, settings, markerResult, materialResolver);
        ExportOptions options = exportOptions(settings, exporter);
        ExportResult result = exporter.export(scene, destination, options, progress, cancellation);
        return new Output(processed, markerResult, scene, result);
    }

    public static ExportOptions exportOptions(ProjectSettings settings, ModelExporter exporter) {
        Objects.requireNonNull(settings, "settings");
        Objects.requireNonNull(exporter, "exporter");
        var values = new LinkedHashMap<CanonicalIdentifier, MetadataValue>(
                settings.output().exporterOptions());
        ProjectSettings.TransformSettings transform = settings.transform();

        if (exporter.formatId().equals(CanonicalIdentifier.parse("mcad:obj"))) {
            values.put(CanonicalIdentifier.parse("mcad:transform/origin_offset"), vector(transform.explicitOriginOffset()));
            values.put(CanonicalIdentifier.parse("mcad:transform/rotation_degrees"), vector(transform.rotationDegrees()));
            values.put(CanonicalIdentifier.parse("mcad:transform/unit_scale"),
                    new MetadataValue.DoubleValue(transform.unitScale()));
            values.put(CanonicalIdentifier.parse("mcad:transform/target_axis"),
                    new MetadataValue.StringValue(transform.targetAxis().name().toLowerCase(java.util.Locale.ROOT)));
            values.put(CanonicalIdentifier.parse("mcad:loss_policy"),
                    new MetadataValue.StringValue(settings.output().lossPolicy().name().toLowerCase(java.util.Locale.ROOT)));
        } else if (exporter.formatId().equals(CanonicalIdentifier.parse("mcad:gltf"))) {
            Vec3d offset = transform.explicitOriginOffset();
            values.put(CanonicalIdentifier.parse("mcad:gltf/translation"),
                    vector(new Vec3d(-offset.x(), -offset.y(), -offset.z())));
            values.put(CanonicalIdentifier.parse("mcad:gltf/rotation"), quaternion(eulerQuaternion(transform.rotationDegrees())));
            values.put(CanonicalIdentifier.parse("mcad:gltf/scale"),
                    vector(new Vec3d(transform.unitScale(), transform.unitScale(), transform.unitScale())));
            values.put(CanonicalIdentifier.parse("mcad:gltf/loss-policy"),
                    new MetadataValue.StringValue(
                            settings.output().lossPolicy() == ProjectSettings.LossPolicy.FAIL ? "error" : "warning"));
        }
        return new ExportOptions(values);
    }

    private static MetadataValue.ListValue vector(Vec3d value) {
        return new MetadataValue.ListValue(List.of(
                new MetadataValue.DoubleValue(value.x()),
                new MetadataValue.DoubleValue(value.y()),
                new MetadataValue.DoubleValue(value.z())));
    }

    private static MetadataValue.ListValue quaternion(Quaterniond value) {
        return new MetadataValue.ListValue(List.of(
                new MetadataValue.DoubleValue(value.x()),
                new MetadataValue.DoubleValue(value.y()),
                new MetadataValue.DoubleValue(value.z()),
                new MetadataValue.DoubleValue(value.w())));
    }

    private static Quaterniond eulerQuaternion(Vec3d degrees) {
        double x = Math.toRadians(degrees.x()) * 0.5;
        double y = Math.toRadians(degrees.y()) * 0.5;
        double z = Math.toRadians(degrees.z()) * 0.5;
        double sx = Math.sin(x);
        double cx = Math.cos(x);
        double sy = Math.sin(y);
        double cy = Math.cos(y);
        double sz = Math.sin(z);
        double cz = Math.cos(z);
        return new Quaterniond(
                sx * cy * cz + cx * sy * sz,
                cx * sy * cz - sx * cy * sz,
                cx * cy * sz + sx * sy * cz,
                cx * cy * cz - sx * sy * sz);
    }
}
