/*
 * SPDX-License-Identifier: MPL-2.0
 */
package dev.sakus.mcad.api;


public record Color4d(double red, double green, double blue, double alpha) {
    public Color4d {
        Checks.range(red, 0.0, 1.0, "red");
        Checks.range(green, 0.0, 1.0, "green");
        Checks.range(blue, 0.0, 1.0, "blue");
        Checks.range(alpha, 0.0, 1.0, "alpha");
    }
}
