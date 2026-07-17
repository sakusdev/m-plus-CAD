/*
 * SPDX-License-Identifier: MPL-2.0
 */
package dev.sakus.mcad.materials;

/** Stable classification of how a material resolution was produced. */
public enum MaterialResolutionSource {
    NONE,
    BUILT_IN,
    USER_MAPPING,
    IDENTIFICATION,
    IDENTIFICATION_FALLBACK,
    VERTEX_COLOUR
}
