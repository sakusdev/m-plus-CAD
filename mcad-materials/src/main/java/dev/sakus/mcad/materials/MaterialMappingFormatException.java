/*
 * SPDX-License-Identifier: MPL-2.0
 */
package dev.sakus.mcad.materials;

/** Expected invalid-input error for the versioned material mapping format. */
public final class MaterialMappingFormatException extends IllegalArgumentException {
    private static final long serialVersionUID = 1L;

    public MaterialMappingFormatException(String message) {
        super(message);
    }

    public MaterialMappingFormatException(String message, Throwable cause) {
        super(message, cause);
    }
}
