/*
 * SPDX-License-Identifier: MPL-2.0
 */
package dev.sakus.mcad.materials;

import dev.sakus.mcad.api.AlphaMode;
import dev.sakus.mcad.api.CanonicalIdentifier;
import dev.sakus.mcad.api.Color3d;
import dev.sakus.mcad.api.Color4d;
import dev.sakus.mcad.api.SchemaVersion;
import dev.sakus.mcad.api.UserAssetReference;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.Set;
import java.util.TreeMap;

/** Strict deterministic reader/writer for material mapping schema 1.0. */
public final class MaterialMappingJson {
    private static final int MAX_JSON_CHARS = 2_000_000;
    private static final int MAX_DEPTH = 64;
    private static final int MAX_ASSETS_PER_MATERIAL = 256;

    private MaterialMappingJson() {
    }

    public static MaterialMappingDocument read(String json) {
        if (json == null) {
            throw new MaterialMappingFormatException("JSON must not be null");
        }
        if (json.length() > MAX_JSON_CHARS) {
            throw new MaterialMappingFormatException("material mapping JSON is too large");
        }
        try {
            Object rootValue = new Parser(json).parse();
            Map<String, Object> root = object(rootValue, "root");
            requireKeys(root, Set.of("schemaVersion", "mappings"), "root");

            Map<String, Object> versionValue = object(root.get("schemaVersion"), "schemaVersion");
            requireKeys(versionValue, Set.of("major", "minor"), "schemaVersion");
            SchemaVersion version = new SchemaVersion(
                    integer(versionValue.get("major"), "schemaVersion.major"),
                    integer(versionValue.get("minor"), "schemaVersion.minor"));
            if (!MaterialMappingDocument.CURRENT_SCHEMA_VERSION.equals(version)) {
                throw new MaterialMappingFormatException("unsupported material mapping schema version: " + version);
            }

            List<Object> mappingValues = array(root.get("mappings"), "mappings");
            if (mappingValues.size() > MaterialMappingDocument.MAX_MAPPINGS) {
                throw new MaterialMappingFormatException("too many material mappings: " + mappingValues.size());
            }
            var mappings = new TreeMap<CanonicalIdentifier, MaterialMapping>();
            for (int index = 0; index < mappingValues.size(); index++) {
                String location = "mappings[" + index + "]";
                Map<String, Object> mappingValue = object(mappingValues.get(index), location);
                requireKeys(mappingValue, Set.of("blockId", "material"), location);
                CanonicalIdentifier blockId = CanonicalIdentifier.parse(
                        string(mappingValue.get("blockId"), location + ".blockId"));
                MaterialMapping previous = mappings.put(blockId, readMaterial(
                        object(mappingValue.get("material"), location + ".material"),
                        location + ".material"));
                if (previous != null) {
                    throw new MaterialMappingFormatException("duplicate mapping for blockId: " + blockId);
                }
            }
            return new MaterialMappingDocument(version, mappings);
        } catch (MaterialMappingFormatException exception) {
            throw exception;
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw new MaterialMappingFormatException("invalid material mapping: " + exception.getMessage(), exception);
        }
    }

    public static MaterialMappingDocument read(Path path) throws IOException {
        Path checked = path.toAbsolutePath().normalize();
        if (!Files.isRegularFile(checked)) {
            throw new IOException("material mapping is not a regular file: " + checked);
        }
        long size = Files.size(checked);
        if (size > MAX_JSON_CHARS * 4L) {
            throw new MaterialMappingFormatException("material mapping file is too large");
        }
        return read(Files.readString(checked, StandardCharsets.UTF_8));
    }

    public static String write(MaterialMappingDocument document) {
        StringBuilder output = new StringBuilder();
        output.append("{\n");
        output.append("  \"schemaVersion\": {\"major\": ")
                .append(document.schemaVersion().major())
                .append(", \"minor\": ")
                .append(document.schemaVersion().minor())
                .append("},\n");
        output.append("  \"mappings\": [");
        if (!document.mappings().isEmpty()) {
            output.append('\n');
        }
        int mappingIndex = 0;
        for (var entry : document.mappings().entrySet()) {
            if (mappingIndex++ > 0) {
                output.append(",\n");
            }
            output.append("    {\n");
            output.append("      \"blockId\": ");
            appendString(output, entry.getKey().toString());
            output.append(",\n      \"material\": ");
            appendMaterial(output, entry.getValue(), 6);
            output.append("\n    }");
        }
        if (!document.mappings().isEmpty()) {
            output.append('\n').append("  ");
        }
        output.append("]\n}").append('\n');
        return output.toString();
    }

    public static void write(Path path, MaterialMappingDocument document) throws IOException {
        Path destination = path.toAbsolutePath().normalize();
        Path parent = destination.getParent();
        if (parent == null || !Files.isDirectory(parent)) {
            throw new IOException("material mapping parent directory does not exist: " + parent);
        }
        String prefix = "." + destination.getFileName() + ".";
        Path temporary = Files.createTempFile(parent, prefix, ".tmp");
        boolean moved = false;
        try {
            Files.writeString(temporary, write(document), StandardCharsets.UTF_8);
            try {
                Files.move(
                        temporary,
                        destination,
                        StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException exception) {
                Files.move(temporary, destination, StandardCopyOption.REPLACE_EXISTING);
            }
            moved = true;
        } finally {
            if (!moved) {
                Files.deleteIfExists(temporary);
            }
        }
    }

    private static MaterialMapping readMaterial(Map<String, Object> value, String location) {
        requireKeys(value, Set.of(
                "stableId",
                "name",
                "baseColour",
                "metallic",
                "roughness",
                "emissiveColour",
                "emissiveStrength",
                "alphaMode",
                "alphaCutoff",
                "assets"), location);
        List<Object> assetValues = array(value.get("assets"), location + ".assets");
        if (assetValues.size() > MAX_ASSETS_PER_MATERIAL) {
            throw new MaterialMappingFormatException("too many assets at " + location);
        }
        var assets = new ArrayList<UserAssetReference>();
        for (int index = 0; index < assetValues.size(); index++) {
            String assetLocation = location + ".assets[" + index + "]";
            Map<String, Object> asset = object(assetValues.get(index), assetLocation);
            requireKeys(asset, Set.of("projectRelativePath", "copyOnExport"), assetLocation);
            assets.add(new UserAssetReference(
                    string(asset.get("projectRelativePath"), assetLocation + ".projectRelativePath"),
                    bool(asset.get("copyOnExport"), assetLocation + ".copyOnExport")));
        }
        Object cutoffValue = value.get("alphaCutoff");
        OptionalDouble cutoff = cutoffValue == null
                ? OptionalDouble.empty()
                : OptionalDouble.of(number(cutoffValue, location + ".alphaCutoff"));
        return new MaterialMapping(
                string(value.get("stableId"), location + ".stableId"),
                string(value.get("name"), location + ".name"),
                colour4(value.get("baseColour"), location + ".baseColour"),
                number(value.get("metallic"), location + ".metallic"),
                number(value.get("roughness"), location + ".roughness"),
                colour3(value.get("emissiveColour"), location + ".emissiveColour"),
                number(value.get("emissiveStrength"), location + ".emissiveStrength"),
                alphaMode(value.get("alphaMode"), location + ".alphaMode"),
                cutoff,
                assets);
    }

    private static void appendMaterial(StringBuilder output, MaterialMapping material, int indent) {
        String prefix = " ".repeat(indent);
        String inner = " ".repeat(indent + 2);
        output.append("{\n");
        output.append(inner).append("\"stableId\": ");
        appendString(output, material.stableId());
        output.append(",\n").append(inner).append("\"name\": ");
        appendString(output, material.name());
        output.append(",\n").append(inner).append("\"baseColour\": ");
        appendColour(output, material.baseColour().red(), material.baseColour().green(),
                material.baseColour().blue(), material.baseColour().alpha());
        output.append(",\n").append(inner).append("\"metallic\": ").append(format(material.metallic()));
        output.append(",\n").append(inner).append("\"roughness\": ").append(format(material.roughness()));
        output.append(",\n").append(inner).append("\"emissiveColour\": ");
        appendColour(output, material.emissiveColour().red(), material.emissiveColour().green(),
                material.emissiveColour().blue());
        output.append(",\n").append(inner).append("\"emissiveStrength\": ")
                .append(format(material.emissiveStrength()));
        output.append(",\n").append(inner).append("\"alphaMode\": ");
        appendString(output, material.alphaMode().name());
        output.append(",\n").append(inner).append("\"alphaCutoff\": ");
        if (material.alphaCutoff().isPresent()) {
            output.append(format(material.alphaCutoff().getAsDouble()));
        } else {
            output.append("null");
        }
        output.append(",\n").append(inner).append("\"assets\": [");
        if (!material.externalUserAssetReferences().isEmpty()) {
            output.append('\n');
        }
        for (int index = 0; index < material.externalUserAssetReferences().size(); index++) {
            if (index > 0) {
                output.append(",\n");
            }
            UserAssetReference asset = material.externalUserAssetReferences().get(index);
            output.append(" ".repeat(indent + 4)).append("{\"projectRelativePath\": ");
            appendString(output, asset.projectRelativePath());
            output.append(", \"copyOnExport\": ").append(asset.copyOnExport()).append('}');
        }
        if (!material.externalUserAssetReferences().isEmpty()) {
            output.append('\n').append(inner);
        }
        output.append("]\n").append(prefix).append('}');
    }

    private static void appendColour(StringBuilder output, double... components) {
        output.append('[');
        for (int index = 0; index < components.length; index++) {
            if (index > 0) {
                output.append(", ");
            }
            output.append(format(components[index]));
        }
        output.append(']');
    }

    private static String format(double value) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException("cannot serialize non-finite number");
        }
        return value == 0.0 ? "0.0" : Double.toString(value);
    }

    private static void appendString(StringBuilder output, String value) {
        output.append('"');
        for (int index = 0; index < value.length(); index++) {
            char item = value.charAt(index);
            switch (item) {
                case '"' -> output.append("\\\"");
                case '\\' -> output.append("\\\\");
                case '\b' -> output.append("\\b");
                case '\f' -> output.append("\\f");
                case '\n' -> output.append("\\n");
                case '\r' -> output.append("\\r");
                case '\t' -> output.append("\\t");
                default -> {
                    if (item < 0x20) {
                        output.append(String.format("\\u%04x", (int) item));
                    } else {
                        output.append(item);
                    }
                }
            }
        }
        output.append('"');
    }

    private static Color4d colour4(Object value, String location) {
        List<Object> components = array(value, location);
        if (components.size() != 4) {
            throw new MaterialMappingFormatException(location + " must contain four components");
        }
        return new Color4d(
                number(components.get(0), location + "[0]"),
                number(components.get(1), location + "[1]"),
                number(components.get(2), location + "[2]"),
                number(components.get(3), location + "[3]"));
    }

    private static Color3d colour3(Object value, String location) {
        List<Object> components = array(value, location);
        if (components.size() != 3) {
            throw new MaterialMappingFormatException(location + " must contain three components");
        }
        return new Color3d(
                number(components.get(0), location + "[0]"),
                number(components.get(1), location + "[1]"),
                number(components.get(2), location + "[2]"));
    }

    private static AlphaMode alphaMode(Object value, String location) {
        try {
            return AlphaMode.valueOf(string(value, location));
        } catch (IllegalArgumentException exception) {
            throw new MaterialMappingFormatException("invalid alpha mode at " + location, exception);
        }
    }

    private static void requireKeys(Map<String, Object> value, Set<String> expected, String location) {
        if (!value.keySet().equals(expected)) {
            var missing = new TreeMap<String, Boolean>();
            for (String key : expected) {
                if (!value.containsKey(key)) {
                    missing.put(key, Boolean.TRUE);
                }
            }
            var extra = new TreeMap<String, Boolean>();
            for (String key : value.keySet()) {
                if (!expected.contains(key)) {
                    extra.put(key, Boolean.TRUE);
                }
            }
            throw new MaterialMappingFormatException(
                    "unexpected fields at " + location + "; missing=" + missing.keySet() + ", extra=" + extra.keySet());
        }
    }

    private static Map<String, Object> object(Object value, String location) {
        if (!(value instanceof Map<?, ?> raw)) {
            throw new MaterialMappingFormatException(location + " must be an object");
        }
        var result = new LinkedHashMap<String, Object>();
        for (var entry : raw.entrySet()) {
            if (!(entry.getKey() instanceof String key)) {
                throw new MaterialMappingFormatException(location + " contains a non-string key");
            }
            result.put(key, entry.getValue());
        }
        return result;
    }

    private static List<Object> array(Object value, String location) {
        if (!(value instanceof List<?> raw)) {
            throw new MaterialMappingFormatException(location + " must be an array");
        }
        return new ArrayList<>(raw);
    }

    private static String string(Object value, String location) {
        if (!(value instanceof String result)) {
            throw new MaterialMappingFormatException(location + " must be a string");
        }
        return result;
    }

    private static boolean bool(Object value, String location) {
        if (!(value instanceof Boolean result)) {
            throw new MaterialMappingFormatException(location + " must be a boolean");
        }
        return result;
    }

    private static int integer(Object value, String location) {
        if (!(value instanceof BigDecimal number)) {
            throw new MaterialMappingFormatException(location + " must be an integer");
        }
        try {
            return number.intValueExact();
        } catch (ArithmeticException exception) {
            throw new MaterialMappingFormatException(location + " must be an exact 32-bit integer", exception);
        }
    }

    private static double number(Object value, String location) {
        if (!(value instanceof BigDecimal number)) {
            throw new MaterialMappingFormatException(location + " must be a number");
        }
        double result = number.doubleValue();
        if (!Double.isFinite(result)) {
            throw new MaterialMappingFormatException(location + " must be finite");
        }
        return result;
    }

    private static final class Parser {
        private final String input;
        private int offset;

        private Parser(String input) {
            this.input = input;
        }

        private Object parse() {
            skipWhitespace();
            Object value = parseValue(0);
            skipWhitespace();
            if (offset != input.length()) {
                fail("trailing content");
            }
            return value;
        }

        private Object parseValue(int depth) {
            if (depth > MAX_DEPTH) {
                fail("maximum nesting depth exceeded");
            }
            skipWhitespace();
            if (offset >= input.length()) {
                fail("unexpected end of input");
            }
            return switch (input.charAt(offset)) {
                case '{' -> parseObject(depth + 1);
                case '[' -> parseArray(depth + 1);
                case '"' -> parseString();
                case 't' -> parseLiteral("true", Boolean.TRUE);
                case 'f' -> parseLiteral("false", Boolean.FALSE);
                case 'n' -> parseLiteral("null", null);
                default -> parseNumber();
            };
        }

        private Map<String, Object> parseObject(int depth) {
            expect('{');
            var result = new LinkedHashMap<String, Object>();
            skipWhitespace();
            if (consume('}')) {
                return result;
            }
            while (true) {
                skipWhitespace();
                if (offset >= input.length() || input.charAt(offset) != '"') {
                    fail("object key must be a string");
                }
                String key = parseString();
                skipWhitespace();
                expect(':');
                if (result.containsKey(key)) {
                    fail("duplicate object key: " + key);
                }
                result.put(key, parseValue(depth));
                skipWhitespace();
                if (consume('}')) {
                    return result;
                }
                expect(',');
            }
        }

        private List<Object> parseArray(int depth) {
            expect('[');
            var result = new ArrayList<Object>();
            skipWhitespace();
            if (consume(']')) {
                return result;
            }
            while (true) {
                result.add(parseValue(depth));
                skipWhitespace();
                if (consume(']')) {
                    return result;
                }
                expect(',');
            }
        }

        private String parseString() {
            expect('"');
            StringBuilder result = new StringBuilder();
            while (offset < input.length()) {
                char item = input.charAt(offset++);
                if (item == '"') {
                    return result.toString();
                }
                if (item == '\\') {
                    if (offset >= input.length()) {
                        fail("unterminated escape");
                    }
                    char escaped = input.charAt(offset++);
                    switch (escaped) {
                        case '"', '\\', '/' -> result.append(escaped);
                        case 'b' -> result.append('\b');
                        case 'f' -> result.append('\f');
                        case 'n' -> result.append('\n');
                        case 'r' -> result.append('\r');
                        case 't' -> result.append('\t');
                        case 'u' -> result.append(parseUnicodeEscape());
                        default -> fail("invalid escape sequence");
                    }
                } else {
                    if (item < 0x20) {
                        fail("unescaped control character");
                    }
                    result.append(item);
                }
            }
            fail("unterminated string");
            return "";
        }

        private char parseUnicodeEscape() {
            if (offset + 4 > input.length()) {
                fail("incomplete unicode escape");
            }
            int value = 0;
            for (int index = 0; index < 4; index++) {
                int digit = Character.digit(input.charAt(offset++), 16);
                if (digit < 0) {
                    fail("invalid unicode escape");
                }
                value = value * 16 + digit;
            }
            return (char) value;
        }

        private Object parseLiteral(String literal, Object value) {
            if (!input.startsWith(literal, offset)) {
                fail("invalid literal");
            }
            offset += literal.length();
            return value;
        }

        private BigDecimal parseNumber() {
            int start = offset;
            consume('-');
            if (consume('0')) {
                if (offset < input.length() && Character.isDigit(input.charAt(offset))) {
                    fail("leading zero in number");
                }
            } else {
                requireDigits();
            }
            if (consume('.')) {
                requireDigits();
            }
            if (offset < input.length() && (input.charAt(offset) == 'e' || input.charAt(offset) == 'E')) {
                offset++;
                if (offset < input.length() && (input.charAt(offset) == '+' || input.charAt(offset) == '-')) {
                    offset++;
                }
                requireDigits();
            }
            if (start == offset) {
                fail("expected JSON value");
            }
            try {
                return new BigDecimal(input.substring(start, offset));
            } catch (NumberFormatException exception) {
                throw new MaterialMappingFormatException("invalid number at offset " + start, exception);
            }
        }

        private void requireDigits() {
            int start = offset;
            while (offset < input.length() && Character.isDigit(input.charAt(offset))) {
                offset++;
            }
            if (start == offset) {
                fail("expected digit");
            }
        }

        private void skipWhitespace() {
            while (offset < input.length()) {
                char item = input.charAt(offset);
                if (item == ' ' || item == '\n' || item == '\r' || item == '\t') {
                    offset++;
                } else {
                    return;
                }
            }
        }

        private void expect(char expected) {
            if (!consume(expected)) {
                fail("expected '" + expected + "'");
            }
        }

        private boolean consume(char expected) {
            if (offset < input.length() && input.charAt(offset) == expected) {
                offset++;
                return true;
            }
            return false;
        }

        private void fail(String message) {
            throw new MaterialMappingFormatException(message + " at offset " + offset);
        }
    }
}
