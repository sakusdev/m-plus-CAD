/*
 * SPDX-License-Identifier: MPL-2.0
 */
package dev.sakus.mcad.minecraft.gui.settings;

import dev.sakus.mcad.api.ProjectSettings;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Objects;

/**
 * Safe project-root-relative persistence for versioned settings documents.
 */
public final class ProjectSettingsStore {
    private static final int MAX_SETTINGS_FILE_BYTES = 16 * 1024 * 1024;

    private final Path root;
    private final ProjectSettingsCodec codec;

    public ProjectSettingsStore(Path root, ProjectSettingsCodec codec) throws IOException {
        Objects.requireNonNull(root, "root");
        this.codec = Objects.requireNonNull(codec, "codec");
        Path normalizedRoot = root.toAbsolutePath().normalize();
        Files.createDirectories(normalizedRoot);
        rejectSymbolicLink(normalizedRoot);
        this.root = normalizedRoot;
    }

    public Path root() {
        return root;
    }

    public Path save(Path relativePath, ProjectSettings settings) throws IOException {
        Objects.requireNonNull(settings, "settings");
        Path target = resolveSafe(relativePath);
        Path parent = Objects.requireNonNull(target.getParent(), "settings parent");
        Files.createDirectories(parent);
        verifyExistingPathComponents(parent);
        if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
            rejectSymbolicLink(target);
            if (!Files.isRegularFile(target, LinkOption.NOFOLLOW_LINKS)) {
                throw new IOException("settings destination is not a regular file: " + target);
            }
        }

        byte[] document = codec.encode(settings);
        Path temporary = Files.createTempFile(parent, ".mcad-settings-", ".tmp");
        boolean moved = false;
        try {
            try (FileChannel channel = FileChannel.open(
                    temporary,
                    StandardOpenOption.WRITE,
                    StandardOpenOption.TRUNCATE_EXISTING)) {
                ByteBuffer buffer = ByteBuffer.wrap(document);
                while (buffer.hasRemaining()) {
                    channel.write(buffer);
                }
                channel.force(true);
            }
            rejectSymbolicLink(temporary);
            try {
                Files.move(
                        temporary,
                        target,
                        java.nio.file.StandardCopyOption.ATOMIC_MOVE,
                        java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException exception) {
                Files.move(temporary, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
            moved = true;
            rejectSymbolicLink(target);
            return target;
        } finally {
            if (!moved) {
                Files.deleteIfExists(temporary);
            }
        }
    }

    public DecodedProjectSettings load(Path relativePath, ProjectSettings migrationDefaults) throws IOException {
        Objects.requireNonNull(migrationDefaults, "migrationDefaults");
        Path target = resolveSafe(relativePath);
        verifyExistingPathComponents(target);
        rejectSymbolicLink(target);
        if (!Files.isRegularFile(target, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("settings source is not a regular file: " + target);
        }
        long size = Files.size(target);
        if (size > MAX_SETTINGS_FILE_BYTES) {
            throw new IOException("settings file exceeds " + MAX_SETTINGS_FILE_BYTES + " bytes");
        }
        byte[] document;
        try (InputStream input = Files.newInputStream(target, StandardOpenOption.READ)) {
            document = input.readNBytes(MAX_SETTINGS_FILE_BYTES + 1);
        }
        if (document.length > MAX_SETTINGS_FILE_BYTES) {
            throw new IOException("settings file exceeds " + MAX_SETTINGS_FILE_BYTES + " bytes");
        }
        return codec.decode(document, migrationDefaults);
    }

    private Path resolveSafe(Path relativePath) {
        Objects.requireNonNull(relativePath, "relativePath");
        if (relativePath.isAbsolute()) {
            throw new IllegalArgumentException("settings path must be relative to the project root");
        }
        Path normalized = relativePath.normalize();
        if (normalized.getNameCount() == 0 || normalized.toString().isBlank()) {
            throw new IllegalArgumentException("settings path must not be empty");
        }
        if (normalized.startsWith("..")) {
            throw new IllegalArgumentException("settings path must not escape the project root");
        }
        Path resolved = root.resolve(normalized).normalize();
        if (!resolved.startsWith(root)) {
            throw new IllegalArgumentException("settings path must not escape the project root");
        }
        return resolved;
    }

    private void verifyExistingPathComponents(Path path) throws IOException {
        Path normalized = path.toAbsolutePath().normalize();
        if (!normalized.startsWith(root)) {
            throw new IOException("path escaped settings root: " + path);
        }
        Path current = root;
        rejectSymbolicLink(current);
        Path relative = root.relativize(normalized);
        for (Path component : relative) {
            current = current.resolve(component);
            if (Files.exists(current, LinkOption.NOFOLLOW_LINKS)) {
                rejectSymbolicLink(current);
            }
        }
    }

    private static void rejectSymbolicLink(Path path) throws IOException {
        if (Files.isSymbolicLink(path)) {
            throw new IOException("symbolic links are not allowed in settings paths: " + path);
        }
    }
}
