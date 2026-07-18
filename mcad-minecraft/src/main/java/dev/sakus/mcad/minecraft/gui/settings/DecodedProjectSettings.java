/*
 * SPDX-License-Identifier: MPL-2.0
 */
package dev.sakus.mcad.minecraft.gui.settings;

import dev.sakus.mcad.api.ProjectSettings;

import java.util.Objects;

/**
 * Result of loading a versioned settings document.
 */
public record DecodedProjectSettings(
        ProjectSettings settings,
        int sourceStorageVersion,
        boolean migrated) {
    public DecodedProjectSettings {
        settings = Objects.requireNonNull(settings, "settings");
        if (sourceStorageVersion < 0) {
            throw new IllegalArgumentException("sourceStorageVersion must be non-negative");
        }
    }
}
