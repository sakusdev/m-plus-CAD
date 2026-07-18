/*
 * SPDX-License-Identifier: MPL-2.0
 */
package dev.sakus.mcad.gltf;

import java.util.Iterator;
import java.util.List;
import java.util.Map;

final class JsonWriter {
    private JsonWriter() {
    }

    static String write(Object value) {
        StringBuilder output = new StringBuilder(4096);
        append(output, value);
        return output.toString();
    }

    private static void append(StringBuilder output, Object value) {
        if (value == null) {
            output.append("null");
        } else if (value instanceof String string) {
            appendString(output, string);
        } else if (value instanceof Boolean bool) {
            output.append(bool.booleanValue());
        } else if (value instanceof Number number) {
            appendNumber(output, number);
        } else if (value instanceof Map<?, ?> map) {
            appendMap(output, map);
        } else if (value instanceof List<?> list) {
            appendList(output, list);
        } else {
            throw new IllegalArgumentException("unsupported JSON value: " + value.getClass().getName());
        }
    }

    private static void appendNumber(StringBuilder output, Number number) {
        if (number instanceof Double doubleValue && !Double.isFinite(doubleValue.doubleValue())) {
            throw new IllegalArgumentException("JSON number must be finite");
        }
        if (number instanceof Float floatValue && !Float.isFinite(floatValue.floatValue())) {
            throw new IllegalArgumentException("JSON number must be finite");
        }
        output.append(number);
    }

    private static void appendMap(StringBuilder output, Map<?, ?> map) {
        output.append('{');
        Iterator<? extends Map.Entry<?, ?>> iterator = map.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<?, ?> entry = iterator.next();
            if (!(entry.getKey() instanceof String key)) {
                throw new IllegalArgumentException("JSON object key must be a string");
            }
            appendString(output, key);
            output.append(':');
            append(output, entry.getValue());
            if (iterator.hasNext()) {
                output.append(',');
            }
        }
        output.append('}');
    }

    private static void appendList(StringBuilder output, List<?> list) {
        output.append('[');
        Iterator<?> iterator = list.iterator();
        while (iterator.hasNext()) {
            append(output, iterator.next());
            if (iterator.hasNext()) {
                output.append(',');
            }
        }
        output.append(']');
    }

    private static void appendString(StringBuilder output, String value) {
        output.append('"');
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            switch (character) {
                case '"' -> output.append("\\\"");
                case '\\' -> output.append("\\\\");
                case '\b' -> output.append("\\b");
                case '\f' -> output.append("\\f");
                case '\n' -> output.append("\\n");
                case '\r' -> output.append("\\r");
                case '\t' -> output.append("\\t");
                default -> {
                    if (character < 0x20 || Character.isSurrogate(character)) {
                        appendUnicodeEscape(output, character);
                    } else {
                        output.append(character);
                    }
                }
            }
        }
        output.append('"');
    }

    private static void appendUnicodeEscape(StringBuilder output, char value) {
        output.append("\\u");
        String hexadecimal = Integer.toHexString(value);
        output.append("0".repeat(4 - hexadecimal.length()));
        output.append(hexadecimal);
    }
}
