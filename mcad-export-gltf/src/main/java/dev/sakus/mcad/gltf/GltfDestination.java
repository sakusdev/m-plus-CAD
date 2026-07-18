/*
 * SPDX-License-Identifier: MPL-2.0
 */
package dev.sakus.mcad.gltf;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

record GltfDestination(Format format, Path primary, Optional<Path> binary, String binaryUri) {
    static GltfDestination parse(Path requested) {
        Path normalized = requested.toAbsolutePath().normalize();
        Path parent = normalized.getParent();
        if (parent == null || !Files.isDirectory(parent)) {
            throw new InvalidDestinationException("destination parent directory does not exist");
        }
        String fileName = normalized.getFileName().toString();
        if (fileName.isBlank() || fileName.equals(".") || fileName.equals("..")
                || containsControl(fileName) || fileName.contains("\\") || fileName.contains(":")) {
            throw new InvalidDestinationException("destination file name is invalid or not portable");
        }
        String lower = fileName.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".glb")) {
            return new GltfDestination(Format.GLB, normalized, Optional.empty(), "");
        }
        if (!lower.endsWith(".gltf")) {
            throw new InvalidDestinationException("destination extension must be .gltf or .glb");
        }
        String baseName = fileName.substring(0, fileName.length() - 5);
        if (baseName.isBlank()) {
            throw new InvalidDestinationException("destination base name must not be empty");
        }
        Path binary = parent.resolve(baseName + ".bin").normalize();
        if (!Objects.equals(binary.getParent(), parent)) {
            throw new InvalidDestinationException("derived binary path escapes destination directory");
        }
        return new GltfDestination(
                Format.GLTF,
                normalized,
                Optional.of(binary),
                URLEncoder.encode(binary.getFileName().toString(), StandardCharsets.UTF_8).replace("+", "%20"));
    }

    private static boolean containsControl(String value) {
        for (int index = 0; index < value.length(); index++) {
            if (Character.isISOControl(value.charAt(index))) {
                return true;
            }
        }
        return false;
    }

    enum Format {
        GLTF,
        GLB
    }

    static final class InvalidDestinationException extends IllegalArgumentException {
        private static final long serialVersionUID = 1L;

        InvalidDestinationException(String message) {
            super(message);
        }
    }
}
