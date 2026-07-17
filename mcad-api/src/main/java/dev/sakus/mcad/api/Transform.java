/*
 * SPDX-License-Identifier: MPL-2.0
 */
package dev.sakus.mcad.api;


public record Transform(Vec3d translation, Quaterniond rotation, Vec3d scale) {
    public static final Transform IDENTITY = new Transform(Vec3d.ZERO, Quaterniond.IDENTITY, Vec3d.ONE);

    public Transform {
        Checks.notNull(translation, "translation");
        Checks.notNull(rotation, "rotation");
        Checks.notNull(scale, "scale");
        if (scale.x() == 0.0 || scale.y() == 0.0 || scale.z() == 0.0) {
            throw new IllegalArgumentException("scale components must be non-zero");
        }
    }
}
