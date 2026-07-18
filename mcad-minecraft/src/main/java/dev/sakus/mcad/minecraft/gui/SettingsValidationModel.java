/*
 * SPDX-License-Identifier: MPL-2.0
 */
package dev.sakus.mcad.minecraft.gui;

import dev.sakus.mcad.api.ProjectSettings;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Deterministic cross-field and exporter-capability validation for the settings shell.
 */
public record SettingsValidationModel(List<Diagnostic> diagnostics) {
    public enum Severity {
        ERROR,
        WARNING,
        INFO
    }

    public record Diagnostic(
            Severity severity,
            SettingsSection section,
            SettingsField field,
            String code,
            String message) {
        public Diagnostic {
            Objects.requireNonNull(severity, "severity");
            Objects.requireNonNull(section, "section");
            Objects.requireNonNull(field, "field");
            if (field.section() != section) {
                throw new IllegalArgumentException("diagnostic field belongs to a different section");
            }
            code = requireStableCode(code);
            message = Objects.requireNonNull(message, "message");
            if (message.isBlank()) {
                throw new IllegalArgumentException("diagnostic message must not be blank");
            }
        }
    }

    private static final Comparator<Diagnostic> ORDER = Comparator
            .comparing(Diagnostic::severity)
            .thenComparing(Diagnostic::section)
            .thenComparing(Diagnostic::field)
            .thenComparing(Diagnostic::code);

    public SettingsValidationModel {
        Objects.requireNonNull(diagnostics, "diagnostics");
        List<Diagnostic> sorted = new ArrayList<>(diagnostics);
        sorted.sort(ORDER);
        diagnostics = List.copyOf(sorted);
    }

    public static SettingsValidationModel validate(
            ProjectSettings settings,
            Optional<ExporterCapabilityModel> capabilities) {
        Objects.requireNonNull(settings, "settings");
        capabilities = Objects.requireNonNull(capabilities, "capabilities");
        List<Diagnostic> diagnostics = new ArrayList<>();

        if (settings.materials().mode() == ProjectSettings.MaterialMode.USER_DEFINED_MAPPING
                && settings.materials().userMappingId().isEmpty()) {
            diagnostics.add(new Diagnostic(
                    Severity.ERROR,
                    SettingsSection.MATERIALS,
                    SettingsField.MATERIALS_USER_MAPPING,
                    "materials.user_mapping.required",
                    "ユーザー定義マテリアルを使用するにはmapping IDを指定してください。"));
        }

        if (settings.transform().originMode() == ProjectSettings.OriginMode.MARKER_DEFINED
                && !settings.markers().enabled()) {
            diagnostics.add(new Diagnostic(
                    Severity.ERROR,
                    SettingsSection.TRANSFORM,
                    SettingsField.TRANSFORM_ORIGIN_MODE,
                    "transform.marker_origin.requires_markers",
                    "マーカー原点を使用するにはMarkersを有効にしてください。"));
        }

        if (settings.markers().previewInterpretation() && !settings.markers().enabled()) {
            diagnostics.add(new Diagnostic(
                    Severity.INFO,
                    SettingsSection.MARKERS,
                    SettingsField.MARKERS_PREVIEW_INTERPRETATION,
                    "markers.preview.inactive",
                    "Markersが無効なため、マーカー解釈プレビューは表示されません。"));
        }

        capabilities.ifPresent(model -> addCapabilityDiagnostics(settings, model, diagnostics));
        return new SettingsValidationModel(diagnostics);
    }

    public boolean valid() {
        return diagnostics.stream().noneMatch(diagnostic -> diagnostic.severity() == Severity.ERROR);
    }

    public List<Diagnostic> forSection(SettingsSection section) {
        Objects.requireNonNull(section, "section");
        return diagnostics.stream()
                .filter(diagnostic -> diagnostic.section() == section)
                .toList();
    }

    private static void addCapabilityDiagnostics(
            ProjectSettings settings,
            ExporterCapabilityModel capabilities,
            List<Diagnostic> diagnostics) {
        Severity lossSeverity = settings.output().lossPolicy() == ProjectSettings.LossPolicy.FAIL
                ? Severity.ERROR
                : Severity.WARNING;

        if (!settings.meshSeparation().orderedKeys().isEmpty()) {
            addUnsupported(
                    capabilities,
                    ExporterCapabilityModel.Control.MULTIPLE_MESHES,
                    diagnostics,
                    lossSeverity,
                    SettingsSection.MESH_SEPARATION,
                    SettingsField.MESH_SEPARATION_ORDERED_KEYS,
                    "mesh_separation.multiple_meshes.unsupported",
                    "選択中の出力形式は複数メッシュに対応していません。");
        }

        if (settings.materials().mode() == ProjectSettings.MaterialMode.VERTEX_COLOURS) {
            addUnsupported(
                    capabilities,
                    ExporterCapabilityModel.Control.VERTEX_COLOURS,
                    diagnostics,
                    lossSeverity,
                    SettingsSection.MATERIALS,
                    SettingsField.MATERIALS_MODE,
                    "materials.vertex_colours.unsupported",
                    "選択中の出力形式は頂点カラーに対応していません。");
        }

        if (settings.animation().enabled()) {
            addUnsupported(
                    capabilities,
                    ExporterCapabilityModel.Control.ANIMATION,
                    diagnostics,
                    lossSeverity,
                    SettingsSection.ANIMATION,
                    SettingsField.ANIMATION_ENABLED,
                    "animation.unsupported",
                    "選択中の出力形式はアニメーションに対応していません。");
        }

        if (settings.collision().enabled()) {
            addUnsupported(
                    capabilities,
                    ExporterCapabilityModel.Control.COLLISION_METADATA,
                    diagnostics,
                    lossSeverity,
                    SettingsSection.COLLISION,
                    SettingsField.COLLISION_ENABLED,
                    "collision.metadata.unsupported",
                    "選択中の出力形式はコリジョンメタデータに対応していません。");
        }
    }

    private static void addUnsupported(
            ExporterCapabilityModel capabilities,
            ExporterCapabilityModel.Control control,
            List<Diagnostic> diagnostics,
            Severity severity,
            SettingsSection section,
            SettingsField field,
            String code,
            String message) {
        if (!capabilities.availability(control).enabled()) {
            diagnostics.add(new Diagnostic(severity, section, field, code, message));
        }
    }

    private static String requireStableCode(String value) {
        Objects.requireNonNull(value, "code");
        if (!value.matches("[a-z0-9_]+(?:\\.[a-z0-9_]+)+")) {
            throw new IllegalArgumentException("invalid diagnostic code: " + value);
        }
        return value;
    }
}
