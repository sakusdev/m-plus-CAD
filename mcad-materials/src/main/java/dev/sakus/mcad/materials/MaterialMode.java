/*
 * SPDX-License-Identifier: MPL-2.0
 */
package dev.sakus.mcad.materials;

/** Material strategy selected by project settings. */
public enum MaterialMode {
    NONE,
    BUILT_IN_SINGLE_COLOUR,
    VERTEX_COLOUR,
    IDENTIFICATION_COLOUR,
    USER_MAPPING
}
