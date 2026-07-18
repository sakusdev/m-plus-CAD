/*
 * SPDX-License-Identifier: MPL-2.0
 */
package dev.sakus.mcad.livelink;

import java.util.List;
import java.util.Objects;
import java.util.function.Function;

/** Small deterministic JSON helpers used by the wire protocol. */
final class LiveLinkJson {
    private LiveLinkJson() {
    }

    static String quote(String value) {
        Objects.requireNonNull(value, "value");
        StringBuilder result = new StringBuilder(value.length() + 2).append('"');
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            switch (character) {
                case '"' -> result.append("\\\"");
                case '\\' -> result.append("\\\\");
                case '\b' -> result.append("\\b");
                case '\f' -> result.append("\\f");
                case '\n' -> result.append("\\n");
                case '\r' -> result.append("\\r");
                case '\t' -> result.append("\\t");
                default -> {
                    if (character < 0x20) {
                        result.append(String.format(java.util.Locale.ROOT, "\\u%04x", (int) character));
                    } else {
                        result.append(character);
                    }
                }
            }
        }
        return result.append('"').toString();
    }

    static String number(double value) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException("JSON numbers must be finite");
        }
        return Double.toString(value);
    }

    static String integer(long value) {
        return Long.toString(value);
    }

    static <T> String array(List<T> values, Function<T, String> encoder) {
        Objects.requireNonNull(values, "values");
        Objects.requireNonNull(encoder, "encoder");
        StringBuilder result = new StringBuilder("[");
        for (int index = 0; index < values.size(); index++) {
            if (index > 0) {
                result.append(',');
            }
            result.append(Objects.requireNonNull(encoder.apply(values.get(index)), "encoded value"));
        }
        return result.append(']').toString();
    }
}
