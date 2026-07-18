/*
 * SPDX-License-Identifier: MPL-2.0
 */
package dev.sakus.mcad.minecraft.gui;

import java.util.Objects;

/**
 * Stable GUI field identifiers mapped to the versioned ProjectSettings areas.
 */
public enum SettingsField {
    SELECTION_START(SettingsSection.SELECTION, "start", "mcad.settings.selection.start"),
    SELECTION_END(SettingsSection.SELECTION, "end", "mcad.settings.selection.end"),
    SELECTION_MAXIMUM_BLOCK_COUNT(
            SettingsSection.SELECTION, "maximum-block-count", "mcad.settings.selection.maximum_block_count"),
    SELECTION_PRESERVE_EMPTY_CELLS(
            SettingsSection.SELECTION, "preserve-empty-cells", "mcad.settings.selection.preserve_empty_cells"),

    GEOMETRY_HIDDEN_FACE_REMOVAL(
            SettingsSection.GEOMETRY, "hidden-face-removal", "mcad.settings.geometry.hidden_face_removal"),
    GEOMETRY_REJECT_DEGENERATE_TRIANGLES(
            SettingsSection.GEOMETRY,
            "reject-degenerate-triangles",
            "mcad.settings.geometry.reject_degenerate_triangles"),

    MESH_SEPARATION_ORDERED_KEYS(
            SettingsSection.MESH_SEPARATION, "ordered-keys", "mcad.settings.mesh_separation.ordered_keys"),

    MATERIALS_MODE(SettingsSection.MATERIALS, "mode", "mcad.settings.materials.mode"),
    MATERIALS_USER_MAPPING(SettingsSection.MATERIALS, "user-mapping", "mcad.settings.materials.user_mapping"),

    TRANSFORM_ORIGIN_MODE(SettingsSection.TRANSFORM, "origin-mode", "mcad.settings.transform.origin_mode"),
    TRANSFORM_ORIGIN_OFFSET(SettingsSection.TRANSFORM, "origin-offset", "mcad.settings.transform.origin_offset"),
    TRANSFORM_ROTATION(SettingsSection.TRANSFORM, "rotation", "mcad.settings.transform.rotation"),
    TRANSFORM_UNIT_SCALE(SettingsSection.TRANSFORM, "unit-scale", "mcad.settings.transform.unit_scale"),
    TRANSFORM_TARGET_AXIS(SettingsSection.TRANSFORM, "target-axis", "mcad.settings.transform.target_axis"),

    MARKERS_ENABLED(SettingsSection.MARKERS, "enabled", "mcad.settings.markers.enabled"),
    MARKERS_CONSUME_SEMANTIC_SOURCES(
            SettingsSection.MARKERS,
            "consume-semantic-sources",
            "mcad.settings.markers.consume_semantic_sources"),
    MARKERS_PREVIEW_INTERPRETATION(
            SettingsSection.MARKERS,
            "preview-interpretation",
            "mcad.settings.markers.preview_interpretation"),

    OPTIMIZATION_GREEDY_MESHING(
            SettingsSection.OPTIMIZATION, "greedy-meshing", "mcad.settings.optimization.greedy_meshing"),
    OPTIMIZATION_INSTANCING(
            SettingsSection.OPTIMIZATION, "instancing", "mcad.settings.optimization.instancing"),
    OPTIMIZATION_PRESERVE_MATERIAL_BOUNDARIES(
            SettingsSection.OPTIMIZATION,
            "preserve-material-boundaries",
            "mcad.settings.optimization.preserve_material_boundaries"),

    ANIMATION_ENABLED(SettingsSection.ANIMATION, "enabled", "mcad.settings.animation.enabled"),
    ANIMATION_FRAMES_PER_SECOND(
            SettingsSection.ANIMATION, "frames-per-second", "mcad.settings.animation.frames_per_second"),

    COLLISION_ENABLED(SettingsSection.COLLISION, "enabled", "mcad.settings.collision.enabled"),
    COLLISION_DEFAULT_KIND(
            SettingsSection.COLLISION, "default-kind", "mcad.settings.collision.default_kind"),

    OUTPUT_EXPORTER(SettingsSection.OUTPUT, "exporter", "mcad.settings.output.exporter"),
    OUTPUT_DESTINATION(SettingsSection.OUTPUT, "destination", "mcad.settings.output.destination"),
    OUTPUT_LOSS_POLICY(SettingsSection.OUTPUT, "loss-policy", "mcad.settings.output.loss_policy"),
    OUTPUT_EXPORTER_OPTIONS(
            SettingsSection.OUTPUT, "exporter-options", "mcad.settings.output.exporter_options"),

    PREVIEW_SELECTION_OUTLINE(
            SettingsSection.PREVIEW, "selection-outline", "mcad.settings.preview.selection_outline"),
    PREVIEW_DIAGNOSTICS_OVERLAY(
            SettingsSection.PREVIEW, "diagnostics-overlay", "mcad.settings.preview.diagnostics_overlay"),
    PREVIEW_CONSUMED_MARKERS(
            SettingsSection.PREVIEW, "consumed-markers", "mcad.settings.preview.consumed_markers"),

    ADVANCED_WORKER_THREADS(
            SettingsSection.ADVANCED, "worker-threads", "mcad.settings.advanced.worker_threads"),
    ADVANCED_MEMORY_LIMIT(
            SettingsSection.ADVANCED, "memory-limit", "mcad.settings.advanced.memory_limit");

    private final SettingsSection section;
    private final String stableId;
    private final String translationKey;

    SettingsField(SettingsSection section, String stableId, String translationKey) {
        this.section = Objects.requireNonNull(section, "section");
        this.stableId = requireStableId(stableId);
        this.translationKey = requireTranslationKey(translationKey);
    }

    public SettingsSection section() {
        return section;
    }

    public String stableId() {
        return stableId;
    }

    public String translationKey() {
        return translationKey;
    }

    private static String requireStableId(String value) {
        Objects.requireNonNull(value, "stableId");
        if (!value.matches("[a-z0-9]+(?:-[a-z0-9]+)*")) {
            throw new IllegalArgumentException("invalid settings field id: " + value);
        }
        return value;
    }

    private static String requireTranslationKey(String value) {
        Objects.requireNonNull(value, "translationKey");
        if (!value.matches("[a-z0-9_.]+")) {
            throw new IllegalArgumentException("invalid translation key: " + value);
        }
        return value;
    }
}
