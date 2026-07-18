/*
 * SPDX-License-Identifier: MPL-2.0
 */
package dev.sakus.mcad.gltf;

import dev.sakus.mcad.api.CanonicalIdentifier;

import java.util.Locale;

final class GltfNames {
    private GltfNames() {
    }

    static String attributeSemantic(CanonicalIdentifier identifier) {
        String raw = identifier.toString().toUpperCase(Locale.ROOT);
        StringBuilder result = new StringBuilder("_MCAD_");
        boolean underscore = false;
        for (int index = 0; index < raw.length(); index++) {
            char character = raw.charAt(index);
            if ((character >= 'A' && character <= 'Z') || (character >= '0' && character <= '9')) {
                result.append(character);
                underscore = false;
            } else if (!underscore) {
                result.append('_');
                underscore = true;
            }
        }
        return result.toString();
    }

    static String sanitize(String name, String fallback) {
        StringBuilder output = new StringBuilder(name.length());
        for (int index = 0; index < name.length(); index++) {
            char character = name.charAt(index);
            output.append(Character.isISOControl(character) ? '_' : character);
        }
        String sanitized = output.toString().trim();
        return sanitized.isEmpty() ? fallback : sanitized;
    }
}
