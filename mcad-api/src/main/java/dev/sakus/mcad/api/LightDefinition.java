/*
 * SPDX-License-Identifier: MPL-2.0
 */
package dev.sakus.mcad.api;


import java.util.OptionalDouble;

public record LightDefinition(
        String stableId,
        String name,
        LightType type,
        Color3d colour,
        double intensity,
        OptionalDouble range,
        OptionalDouble innerConeRadians,
        OptionalDouble outerConeRadians) {

    public LightDefinition {
        Checks.stableId(stableId, "stableId");
        Checks.nonBlank(name, "name");
        Checks.notNull(type, "type");
        Checks.notNull(colour, "colour");
        Checks.finite(intensity, "intensity");
        if (intensity < 0.0) {
            throw new IllegalArgumentException("intensity must be non-negative");
        }
        range = Checks.notNull(range, "range");
        innerConeRadians = Checks.notNull(innerConeRadians, "innerConeRadians");
        outerConeRadians = Checks.notNull(outerConeRadians, "outerConeRadians");
        range.ifPresent(value -> {
            Checks.finite(value, "range");
            if (value <= 0.0) {
                throw new IllegalArgumentException("range must be positive");
            }
        });
        innerConeRadians.ifPresent(value -> Checks.range(value, 0.0, Math.PI, "innerConeRadians"));
        outerConeRadians.ifPresent(value -> Checks.range(value, 0.0, Math.PI, "outerConeRadians"));
        if (type != LightType.SPOT && (innerConeRadians.isPresent() || outerConeRadians.isPresent())) {
            throw new IllegalArgumentException("cone angles are only valid for spot lights");
        }
        if (innerConeRadians.isPresent() && outerConeRadians.isPresent()
                && innerConeRadians.getAsDouble() > outerConeRadians.getAsDouble()) {
            throw new IllegalArgumentException("inner cone must not exceed outer cone");
        }
    }
}
