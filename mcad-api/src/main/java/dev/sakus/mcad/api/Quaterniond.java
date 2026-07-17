/*
 * SPDX-License-Identifier: MPL-2.0
 */
package dev.sakus.mcad.api;


public record Quaterniond(double x, double y, double z, double w) {
    public static final Quaterniond IDENTITY = new Quaterniond(0.0, 0.0, 0.0, 1.0);

    public Quaterniond {
        Checks.finite(x, "x");
        Checks.finite(y, "y");
        Checks.finite(z, "z");
        Checks.finite(w, "w");
        double magnitudeSquared = x * x + y * y + z * z + w * w;
        if (magnitudeSquared == 0.0 || !Double.isFinite(magnitudeSquared)) {
            throw new IllegalArgumentException("quaternion must be non-zero and finite");
        }
    }
}
