/*
 * SPDX-License-Identifier: MPL-2.0
 */
package dev.sakus.mcad.export.obj;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

final class ObjNameAllocator {
    private final Map<String, Integer> counts = new HashMap<>();

    Allocation allocate(String requested, String fallback) {
        Objects.requireNonNull(requested, "requested");
        String sanitized = sanitize(requested, fallback);
        int occurrence = counts.merge(sanitized, 1, Integer::sum);
        String allocated = occurrence == 1 ? sanitized : sanitized + "_" + occurrence;
        return new Allocation(requested, sanitized, allocated, occurrence > 1);
    }

    static String sanitize(String value, String fallback) {
        Objects.requireNonNull(value, "value");
        Objects.requireNonNull(fallback, "fallback");
        StringBuilder result = new StringBuilder(Math.min(value.length(), 160));
        boolean previousUnderscore = false;
        for (int offset = 0; offset < value.length() && result.length() < 128; ) {
            int codePoint = value.codePointAt(offset);
            offset += Character.charCount(codePoint);
            boolean safe = (codePoint >= 'a' && codePoint <= 'z')
                    || (codePoint >= 'A' && codePoint <= 'Z')
                    || (codePoint >= '0' && codePoint <= '9')
                    || codePoint == '.' || codePoint == '-' || codePoint == '_';
            char output = safe ? (char) codePoint : '_';
            if (output == '_') {
                if (previousUnderscore) {
                    continue;
                }
                previousUnderscore = true;
            } else {
                previousUnderscore = false;
            }
            result.append(output);
        }
        String sanitized = result.toString();
        while (sanitized.startsWith(".")) {
            sanitized = sanitized.substring(1);
        }
        while (sanitized.endsWith(".")) {
            sanitized = sanitized.substring(0, sanitized.length() - 1);
        }
        if (sanitized.isBlank() || sanitized.equals(".") || sanitized.equals("..")) {
            sanitized = fallback.toLowerCase(Locale.ROOT);
        }
        return sanitized;
    }

    record Allocation(String requested, String sanitized, String allocated, boolean collided) {
        boolean changed() {
            return !requested.equals(allocated);
        }
    }
}
