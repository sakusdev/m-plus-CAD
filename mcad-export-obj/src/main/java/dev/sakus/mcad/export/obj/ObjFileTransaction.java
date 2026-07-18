/*
 * SPDX-License-Identifier: MPL-2.0
 */
package dev.sakus.mcad.export.obj;

import dev.sakus.mcad.api.ProducedFile;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.DirectoryNotEmptyException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

final class ObjFileTransaction {
    private ObjFileTransaction() {
    }

    static DestinationPair destination(Path requested) {
        Path absolute = requested.toAbsolutePath().normalize();
        Path fileNamePath = absolute.getFileName();
        if (fileNamePath == null) {
            throw new IllegalArgumentException("destination must include an OBJ file name");
        }
        String fileName = fileNamePath.toString();
        if (fileName.indexOf('\0') >= 0 || fileName.contains("\n") || fileName.contains("\r")) {
            throw new IllegalArgumentException("destination file name contains a control character");
        }
        if (!fileName.toLowerCase(Locale.ROOT).endsWith(".obj")) {
            throw new IllegalArgumentException("destination must use the .obj extension");
        }
        String stem = fileName.substring(0, fileName.length() - 4);
        if (stem.isBlank() || stem.equals(".") || stem.equals("..")) {
            throw new IllegalArgumentException("destination OBJ file name is invalid");
        }
        Path parent = absolute.getParent();
        if (parent == null || !Files.isDirectory(parent)) {
            throw new IllegalArgumentException("destination parent directory does not exist");
        }
        Path mtl = parent.resolve(stem + ".mtl").normalize();
        if (!Objects.equals(parent, mtl.getParent())) {
            throw new IllegalArgumentException("derived MTL path escaped the destination directory");
        }
        if (Files.isDirectory(absolute) || Files.isDirectory(mtl)) {
            throw new IllegalArgumentException("destination conflicts with an existing directory");
        }
        return new DestinationPair(absolute, mtl);
    }

    static void commit(
            Path objTemporary,
            Path objFinal,
            Path mtlTemporary,
            Path mtlFinal) throws IOException {
        Path objBackup = null;
        Path mtlBackup = null;
        boolean objInstalled = false;
        boolean mtlInstalled = false;
        try {
            objBackup = backupExisting(objFinal);
            mtlBackup = backupExisting(mtlFinal);
            moveReplace(mtlTemporary, mtlFinal);
            mtlInstalled = true;
            moveReplace(objTemporary, objFinal);
            objInstalled = true;
            deleteQuietly(objBackup);
            deleteQuietly(mtlBackup);
        } catch (IOException failure) {
            IOException rollbackFailure = null;
            try {
                if (objInstalled) {
                    Files.deleteIfExists(objFinal);
                }
                restoreBackup(objBackup, objFinal);
                objBackup = null;
            } catch (IOException exception) {
                rollbackFailure = exception;
            }
            try {
                if (mtlInstalled) {
                    Files.deleteIfExists(mtlFinal);
                }
                restoreBackup(mtlBackup, mtlFinal);
                mtlBackup = null;
            } catch (IOException exception) {
                if (rollbackFailure == null) {
                    rollbackFailure = exception;
                } else {
                    rollbackFailure.addSuppressed(exception);
                }
            }
            if (rollbackFailure != null) {
                failure.addSuppressed(rollbackFailure);
            }
            throw failure;
        } finally {
            deleteQuietly(objBackup);
            deleteQuietly(mtlBackup);
        }
    }

    static ProducedFile producedFile(Path path, String mediaType) throws IOException {
        return new ProducedFile(
                path.getFileName().toString(), Files.size(path),
                Optional.of(mediaType), Optional.of(sha256(path)));
    }

    static void deleteQuietly(Path path) {
        if (path == null) {
            return;
        }
        try {
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
            // Best-effort cleanup. The original export result remains authoritative.
        }
    }

    private static Path backupExisting(Path target) throws IOException {
        if (!Files.exists(target)) {
            return null;
        }
        if (Files.isDirectory(target)) {
            throw new DirectoryNotEmptyException(target.toString());
        }
        Path backup = Files.createTempFile(target.getParent(), ".mcad-backup-", ".tmp");
        moveReplace(target, backup);
        return backup;
    }

    private static void restoreBackup(Path backup, Path target) throws IOException {
        if (backup != null && Files.exists(backup)) {
            moveReplace(backup, target);
        }
    }

    private static void moveReplace(Path source, Path target) throws IOException {
        try {
            Files.move(source, target,
                    StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException exception) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static String sha256(Path path) throws IOException {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
        try (var input = Files.newInputStream(path)) {
            byte[] buffer = new byte[16_384];
            int read;
            while ((read = input.read(buffer)) >= 0) {
                if (read > 0) {
                    digest.update(buffer, 0, read);
                }
            }
        }
        StringBuilder result = new StringBuilder(64);
        for (byte value : digest.digest()) {
            result.append(String.format(Locale.ROOT, "%02x", value & 0xff));
        }
        return result.toString();
    }

    record DestinationPair(Path obj, Path mtl) {
    }
}
