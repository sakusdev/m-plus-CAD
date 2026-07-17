/*
 * SPDX-License-Identifier: MPL-2.0
 */
package dev.sakus.mcad.api;


public record Color3d(double red, double green, double blue) {
    public static final Color3d BLACK = new Color3d(0.0, 0.0, 0.0);

    public Color3d {
        Checks.range(red, 0.0, 1.0, "red");
        Checks.range(green, 0.0, 1.0, "green");
        Checks.range(blue, 0.0, 1.0, "blue");
    }
}
