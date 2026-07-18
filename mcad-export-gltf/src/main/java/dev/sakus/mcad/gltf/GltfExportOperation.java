/*
 * SPDX-License-Identifier: MPL-2.0
 */
package dev.sakus.mcad.gltf;

import static dev.sakus.mcad.gltf.GltfDiagnostics.boundedMessage;
import static dev.sakus.mcad.gltf.GltfDiagnostics.diagnostic;

import dev.sakus.mcad.api.CancellationToken;
import dev.sakus.mcad.api.Diagnostic;
import dev.sakus.mcad.api.DiagnosticSeverity;
import dev.sakus.mcad.api.ExportOptions;
import dev.sakus.mcad.api.ExportResult;
import dev.sakus.mcad.api.ExportStatus;
import dev.sakus.mcad.api.ExporterCapabilities;
import dev.sakus.mcad.api.GeneratedScene;
import dev.sakus.mcad.api.PreflightResult;
import dev.sakus.mcad.api.ProducedFile;
import dev.sakus.mcad.api.ProgressReporter;
import dev.sakus.mcad.api.ProgressUpdate;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.concurrent.CancellationException;

final class GltfExportOperation {
    private GltfExportOperation() {
    }

    static ExportResult run(
            GeneratedScene scene,
            Path destination,
            ExportOptions options,
            ProgressReporter progress,
            CancellationToken cancellation,
            ExporterCapabilities capabilities) throws IOException {
        List<Path> temporaryFiles = new ArrayList<>();
        List<Diagnostic> diagnostics = new ArrayList<>();
        try {
            cancellation.throwIfCancellationRequested();
            report(progress, 0, 4, "Validating scene and options");
            PreflightResult preflight = GltfPreflight.run(scene, options, capabilities);
            diagnostics.addAll(preflight.diagnostics());
            if (!preflight.canExport()) {
                return new ExportResult(ExportStatus.FAILED, List.of(), diagnostics);
            }

            GltfOptions parsed = GltfOptions.parse(options).options();
            GltfDestination output = GltfDestination.parse(destination);
            cancellation.throwIfCancellationRequested();
            report(progress, 1, 4, "Building glTF document");
            GltfBuildOutput build = new GltfDocumentBuilder(scene, parsed, cancellation).build(output);

            cancellation.throwIfCancellationRequested();
            report(progress, 2, 4, "Writing temporary output");
            List<PendingFile> pendingFiles = writeTemporaryFiles(output, build, temporaryFiles);
            List<ProducedFile> producedFiles = describe(pendingFiles);

            cancellation.throwIfCancellationRequested();
            report(progress, 3, 4, "Replacing final output");
            replaceOutputs(pendingFiles);
            temporaryFiles.clear();

            try {
                report(progress, 4, 4, "Export complete");
            } catch (RuntimeException exception) {
                diagnostics.add(diagnostic(DiagnosticSeverity.WARNING, "gltf/progress-callback-failure",
                        "Output was committed, but the completion progress callback failed: "
                                + boundedMessage(exception)));
            }
            return new ExportResult(ExportStatus.SUCCESS, producedFiles, diagnostics);
        } catch (CancellationException exception) {
            deleteQuietly(temporaryFiles);
            diagnostics.add(diagnostic(DiagnosticSeverity.WARNING, "gltf/cancelled",
                    "glTF export was cancelled."));
            return new ExportResult(ExportStatus.CANCELLED, List.of(), diagnostics);
        } catch (GltfDestination.InvalidDestinationException exception) {
            deleteQuietly(temporaryFiles);
            diagnostics.add(diagnostic(DiagnosticSeverity.ERROR, "gltf/invalid-destination", exception.getMessage()));
            return new ExportResult(ExportStatus.FAILED, List.of(), diagnostics);
        } catch (IllegalArgumentException exception) {
            deleteQuietly(temporaryFiles);
            diagnostics.add(diagnostic(DiagnosticSeverity.ERROR, "gltf/invalid-scene", exception.getMessage()));
            return new ExportResult(ExportStatus.FAILED, List.of(), diagnostics);
        } catch (IOException exception) {
            deleteQuietly(temporaryFiles);
            diagnostics.add(diagnostic(DiagnosticSeverity.ERROR, "gltf/io-failure",
                    "I/O failure while exporting glTF: " + boundedMessage(exception)));
            return new ExportResult(ExportStatus.FAILED, List.of(), diagnostics);
        } catch (RuntimeException exception) {
            deleteQuietly(temporaryFiles);
            diagnostics.add(diagnostic(DiagnosticSeverity.ERROR, "gltf/internal-failure",
                    "Internal glTF exporter failure: " + boundedMessage(exception)));
            return new ExportResult(ExportStatus.FAILED, List.of(), diagnostics);
        }
    }

    private static List<PendingFile> writeTemporaryFiles(
            GltfDestination output,
            GltfBuildOutput build,
            List<Path> temporaryFiles) throws IOException {
        List<PendingFile> pendingFiles = new ArrayList<>();
        if (output.format() == GltfDestination.Format.GLB) {
            byte[] content = build.glbBytes();
            addPending(pendingFiles, temporaryFiles, output.primary(), "model/gltf-binary", content);
        } else {
            addPending(pendingFiles, temporaryFiles, output.primary(), "model/gltf+json", build.jsonBytes());
            if (build.binaryBytes().length > 0) {
                addPending(pendingFiles, temporaryFiles, output.binary().orElseThrow(),
                        "application/octet-stream", build.binaryBytes());
            }
        }
        return List.copyOf(pendingFiles);
    }

    private static void addPending(
            List<PendingFile> pendingFiles,
            List<Path> temporaryFiles,
            Path finalPath,
            String mediaType,
            byte[] content) throws IOException {
        Path temporary = createTemporarySibling(finalPath);
        temporaryFiles.add(temporary);
        Files.write(temporary, content);
        pendingFiles.add(new PendingFile(temporary, finalPath, mediaType, content));
    }

    private static List<ProducedFile> describe(List<PendingFile> pendingFiles) {
        List<ProducedFile> producedFiles = new ArrayList<>();
        for (PendingFile pending : pendingFiles) {
            byte[] content = pending.content();
            producedFiles.add(new ProducedFile(
                    pending.finalPath().getFileName().toString(),
                    content.length,
                    Optional.of(pending.mediaType()),
                    Optional.of(sha256(content))));
        }
        return List.copyOf(producedFiles);
    }

    private static Path createTemporarySibling(Path finalPath) throws IOException {
        Path parent = finalPath.getParent();
        if (parent == null) {
            throw new IllegalArgumentException("destination must have a parent directory");
        }
        return Files.createTempFile(parent, ".mcad-gltf-", ".tmp");
    }

    private static void replaceOutputs(List<PendingFile> pendingFiles) throws IOException {
        List<BackupFile> backups = new ArrayList<>();
        List<Path> installed = new ArrayList<>();
        try {
            for (PendingFile pending : pendingFiles) {
                Path finalPath = pending.finalPath();
                if (Files.exists(finalPath)) {
                    if (Files.isDirectory(finalPath)) {
                        throw new IOException("destination is a directory: " + finalPath.getFileName());
                    }
                    Path backup = createTemporarySibling(finalPath);
                    move(finalPath, backup);
                    backups.add(new BackupFile(finalPath, backup));
                }
            }
            for (PendingFile pending : pendingFiles) {
                move(pending.temporaryPath(), pending.finalPath());
                installed.add(pending.finalPath());
            }
        } catch (IOException exception) {
            rollback(installed, backups, exception);
            throw exception;
        }
        for (BackupFile backup : backups) {
            try {
                Files.deleteIfExists(backup.backupPath());
            } catch (IOException ignored) {
                // Replacement is committed; preserving output is safer than rolling it back now.
            }
        }
    }

    private static void rollback(List<Path> installed, List<BackupFile> backups, IOException exception) {
        for (Path finalPath : installed) {
            try {
                Files.deleteIfExists(finalPath);
            } catch (IOException cleanupFailure) {
                exception.addSuppressed(cleanupFailure);
            }
        }
        for (int index = backups.size() - 1; index >= 0; index--) {
            BackupFile backup = backups.get(index);
            try {
                if (Files.exists(backup.backupPath())) {
                    move(backup.backupPath(), backup.finalPath());
                }
            } catch (IOException restoreFailure) {
                exception.addSuppressed(restoreFailure);
            }
        }
    }

    private static void move(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException exception) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static void deleteQuietly(List<Path> paths) {
        for (Path path : paths) {
            try {
                Files.deleteIfExists(path);
            } catch (IOException ignored) {
                // Cleanup is best-effort after a failed or cancelled export.
            }
        }
    }

    private static void report(ProgressReporter progress, long completed, long total, String message) {
        progress.report(new ProgressUpdate("gltf.export", completed, OptionalLong.of(total), message));
    }

    private static String sha256(byte[] bytes) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(bytes);
            StringBuilder hexadecimal = new StringBuilder(digest.length * 2);
            for (byte value : digest) {
                hexadecimal.append(String.format(Locale.ROOT, "%02x", value & 0xff));
            }
            return hexadecimal.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private record PendingFile(Path temporaryPath, Path finalPath, String mediaType, byte[] content) {
        PendingFile {
            content = content.clone();
        }

        @Override
        public byte[] content() {
            return content.clone();
        }
    }

    private record BackupFile(Path finalPath, Path backupPath) {
    }
}
