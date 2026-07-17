/*
 * SPDX-License-Identifier: MPL-2.0
 */
package dev.sakus.mcad.api;


public record Vec3d(double x, double y, double z) {
    public static final Vec3d ZERO = new Vec3d(0.0, 0.0, 0.0);
    public static final Vec3d ONE = new Vec3d(1.0, 1.0, 1.0);

    public Vec3d {
        Checks.finite(x, "x");
        Checks.finite(y, "y");
        Checks.finite(z, "z");
    }
}
