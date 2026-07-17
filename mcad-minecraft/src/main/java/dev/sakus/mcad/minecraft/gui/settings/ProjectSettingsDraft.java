/*
 * SPDX-License-Identifier: MPL-2.0
 */
package dev.sakus.mcad.minecraft.gui.settings;

import dev.sakus.mcad.api.CanonicalIdentifier;
import dev.sakus.mcad.api.ProjectSettings;

import java.util.Objects;

/**
 * Immutable GUI editing state backed directly by the neutral versioned {@link ProjectSettings}.
 */
public record ProjectSettingsDraft(ProjectSettings value, long revision) {
    public ProjectSettingsDraft {
        value = Objects.requireNonNull(value, "value");
        if (revision < 0L) {
            throw new IllegalArgumentException("revision must be non-negative");
        }
    }

    public static ProjectSettingsDraft from(ProjectSettings settings) {
        return new ProjectSettingsDraft(settings, 0L);
    }

    public ProjectSettingsDraft withSelection(ProjectSettings.SelectionSettings selection) {
        return next(new ProjectSettings(
                value.schemaVersion(), selection, value.geometry(), value.meshSeparation(), value.materials(),
                value.transform(), value.markers(), value.optimization(), value.animation(), value.collision(),
                value.output(), value.preview(), value.advanced()));
    }

    public ProjectSettingsDraft withGeometry(ProjectSettings.GeometrySettings geometry) {
        return next(new ProjectSettings(
                value.schemaVersion(), value.selection(), geometry, value.meshSeparation(), value.materials(),
                value.transform(), value.markers(), value.optimization(), value.animation(), value.collision(),
                value.output(), value.preview(), value.advanced()));
    }

    public ProjectSettingsDraft withMeshSeparation(ProjectSettings.MeshSeparationSettings meshSeparation) {
        return next(new ProjectSettings(
                value.schemaVersion(), value.selection(), value.geometry(), meshSeparation, value.materials(),
                value.transform(), value.markers(), value.optimization(), value.animation(), value.collision(),
                value.output(), value.preview(), value.advanced()));
    }

    public ProjectSettingsDraft withMaterials(ProjectSettings.MaterialSettings materials) {
        return next(new ProjectSettings(
                value.schemaVersion(), value.selection(), value.geometry(), value.meshSeparation(), materials,
                value.transform(), value.markers(), value.optimization(), value.animation(), value.collision(),
                value.output(), value.preview(), value.advanced()));
    }

    public ProjectSettingsDraft withTransform(ProjectSettings.TransformSettings transform) {
        return next(new ProjectSettings(
                value.schemaVersion(), value.selection(), value.geometry(), value.meshSeparation(), value.materials(),
                transform, value.markers(), value.optimization(), value.animation(), value.collision(),
                value.output(), value.preview(), value.advanced()));
    }

    public ProjectSettingsDraft withMarkers(ProjectSettings.MarkerSettings markers) {
        return next(new ProjectSettings(
                value.schemaVersion(), value.selection(), value.geometry(), value.meshSeparation(), value.materials(),
                value.transform(), markers, value.optimization(), value.animation(), value.collision(),
                value.output(), value.preview(), value.advanced()));
    }

    public ProjectSettingsDraft withOptimization(ProjectSettings.OptimizationSettings optimization) {
        return next(new ProjectSettings(
                value.schemaVersion(), value.selection(), value.geometry(), value.meshSeparation(), value.materials(),
                value.transform(), value.markers(), optimization, value.animation(), value.collision(),
                value.output(), value.preview(), value.advanced()));
    }

    public ProjectSettingsDraft withAnimation(ProjectSettings.AnimationSettings animation) {
        return next(new ProjectSettings(
                value.schemaVersion(), value.selection(), value.geometry(), value.meshSeparation(), value.materials(),
                value.transform(), value.markers(), value.optimization(), animation, value.collision(),
                value.output(), value.preview(), value.advanced()));
    }

    public ProjectSettingsDraft withCollision(ProjectSettings.CollisionSettings collision) {
        return next(new ProjectSettings(
                value.schemaVersion(), value.selection(), value.geometry(), value.meshSeparation(), value.materials(),
                value.transform(), value.markers(), value.optimization(), value.animation(), collision,
                value.output(), value.preview(), value.advanced()));
    }

    public ProjectSettingsDraft withOutput(ProjectSettings.OutputSettings output) {
        return next(new ProjectSettings(
                value.schemaVersion(), value.selection(), value.geometry(), value.meshSeparation(), value.materials(),
                value.transform(), value.markers(), value.optimization(), value.animation(), value.collision(),
                output, value.preview(), value.advanced()));
    }

    public ProjectSettingsDraft withExporterId(CanonicalIdentifier exporterId) {
        Objects.requireNonNull(exporterId, "exporterId");
        ProjectSettings.OutputSettings previous = value.output();
        return withOutput(new ProjectSettings.OutputSettings(
                exporterId,
                previous.destination(),
                previous.lossPolicy(),
                previous.exporterOptions()));
    }

    public ProjectSettingsDraft withPreview(ProjectSettings.PreviewSettings preview) {
        return next(new ProjectSettings(
                value.schemaVersion(), value.selection(), value.geometry(), value.meshSeparation(), value.materials(),
                value.transform(), value.markers(), value.optimization(), value.animation(), value.collision(),
                value.output(), preview, value.advanced()));
    }

    public ProjectSettingsDraft withAdvanced(ProjectSettings.AdvancedSettings advanced) {
        return next(new ProjectSettings(
                value.schemaVersion(), value.selection(), value.geometry(), value.meshSeparation(), value.materials(),
                value.transform(), value.markers(), value.optimization(), value.animation(), value.collision(),
                value.output(), value.preview(), advanced));
    }

    private ProjectSettingsDraft next(ProjectSettings updated) {
        Objects.requireNonNull(updated, "updated");
        if (updated.equals(value)) {
            return this;
        }
        return new ProjectSettingsDraft(updated, Math.incrementExact(revision));
    }
}
