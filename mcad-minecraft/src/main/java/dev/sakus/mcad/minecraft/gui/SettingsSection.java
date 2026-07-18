/*
 * SPDX-License-Identifier: MPL-2.0
 */
package dev.sakus.mcad.minecraft.gui;

import java.util.List;

/**
 * Stable navigation order for the versioned project settings shell.
 */
public enum SettingsSection {
    SELECTION("selection", "mcad.settings.selection"),
    GEOMETRY("geometry", "mcad.settings.geometry"),
    MESH_SEPARATION("mesh-separation", "mcad.settings.mesh_separation"),
    MATERIALS("materials", "mcad.settings.materials"),
    TRANSFORM("transform", "mcad.settings.transform"),
    MARKERS("markers", "mcad.settings.markers"),
    OPTIMIZATION("optimization", "mcad.settings.optimization"),
    ANIMATION("animation", "mcad.settings.animation"),
    COLLISION("collision", "mcad.settings.collision"),
    OUTPUT("output", "mcad.settings.output"),
    PREVIEW("preview", "mcad.settings.preview"),
    ADVANCED("advanced", "mcad.settings.advanced");

    private static final List<SettingsSection> ORDERED = List.of(values());

    private final String stableId;
    private final String translationKey;

    SettingsSection(String stableId, String translationKey) {
        this.stableId = stableId;
        this.translationKey = translationKey;
    }

    public String stableId() {
        return stableId;
    }

    public String translationKey() {
        return translationKey;
    }

    public static List<SettingsSection> ordered() {
        return ORDERED;
    }
}
