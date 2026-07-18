/*
 * SPDX-License-Identifier: MPL-2.0
 */
package dev.sakus.mcad.minecraft.gui;

import java.util.Objects;

/**
 * Capability-aware enabled state for one GUI control.
 */
public record ControlAvailability(boolean enabled, String explanation) {
    public ControlAvailability {
        explanation = Objects.requireNonNull(explanation, "explanation");
        if (!enabled && explanation.isBlank()) {
            throw new IllegalArgumentException("disabled controls require an explanation");
        }
    }

    public static ControlAvailability available() {
        return new ControlAvailability(true, "");
    }

    public static ControlAvailability unavailable(String explanation) {
        return new ControlAvailability(false, explanation);
    }
}
