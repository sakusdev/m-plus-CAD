/*
 * SPDX-License-Identifier: MPL-2.0
 */
package dev.sakus.mcad.materials;

import dev.sakus.mcad.api.CanonicalIdentifier;
import dev.sakus.mcad.api.Diagnostic;
import dev.sakus.mcad.api.DiagnosticSeverity;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Explicit filesystem validation for user-controlled project-relative material assets. */
public final class ExternalAssetValidator {
    private static final CanonicalIdentifier ROOT_INVALID =
            CanonicalIdentifier.parse("mcad:material_project_root_invalid");
    private static final CanonicalIdentifier ASSET_MISSING =
            CanonicalIdentifier.parse("mcad:material_asset_missing");
    private static final CanonicalIdentifier ASSET_UNSAFE =
            CanonicalIdentifier.parse("mcad:material_asset_unsafe");
    private static final CanonicalIdentifier ASSET_NOT_FILE =
            CanonicalIdentifier.parse("mcad:material_asset_not_file");
    private static final CanonicalIdentifier ASSET_IO_FAILURE =
            CanonicalIdentifier.parse("mcad:material_asset_io_failure");

    private ExternalAssetValidator() {
    }

    public static AssetValidationResult validate(Path projectRoot, MaterialMappingDocument document) {
        Objects.requireNonNull(projectRoot, "projectRoot");
        Objects.requireNonNull(document, "document");
        Path root = projectRoot.toAbsolutePath().normalize();
        if (!Files.isDirectory(root)) {
            return new AssetValidationResult(
                    List.of(),
                    List.of(diagnostic(
                            DiagnosticSeverity.ERROR,
                            ROOT_INVALID,
                            "Material project root is not an existing directory: " + root)));
        }

        var validAssets = new ArrayList<ValidatedUserAsset>();
        var diagnostics = new ArrayList<Diagnostic>();
        final Path realRoot;
        try {
            realRoot = root.toRealPath();
        } catch (IOException exception) {
            return new AssetValidationResult(
                    List.of(),
                    List.of(diagnostic(
                            DiagnosticSeverity.ERROR,
                            ROOT_INVALID,
                            "Material project root cannot be resolved: " + root)));
        }

        for (var mappingEntry : document.mappings().entrySet()) {
            CanonicalIdentifier blockId = mappingEntry.getKey();
            MaterialMapping material = mappingEntry.getValue();
            for (var reference : material.externalUserAssetReferences()) {
                try {
                    Path candidate = root.resolve(reference.projectRelativePath()).normalize();
                    if (!candidate.startsWith(root)) {
                        diagnostics.add(assetDiagnostic(
                                DiagnosticSeverity.ERROR,
                                ASSET_UNSAFE,
                                blockId,
                                reference.projectRelativePath(),
                                "Asset path escapes the project root"));
                        continue;
                    }
                    if (!Files.exists(candidate)) {
                        diagnostics.add(assetDiagnostic(
                                DiagnosticSeverity.WARNING,
                                ASSET_MISSING,
                                blockId,
                                reference.projectRelativePath(),
                                "Referenced user asset is missing"));
                        continue;
                    }
                    Path realCandidate = candidate.toRealPath();
                    if (!realCandidate.startsWith(realRoot)) {
                        diagnostics.add(assetDiagnostic(
                                DiagnosticSeverity.ERROR,
                                ASSET_UNSAFE,
                                blockId,
                                reference.projectRelativePath(),
                                "Referenced user asset resolves outside the project root"));
                        continue;
                    }
                    if (!Files.isRegularFile(realCandidate)) {
                        diagnostics.add(assetDiagnostic(
                                DiagnosticSeverity.ERROR,
                                ASSET_NOT_FILE,
                                blockId,
                                reference.projectRelativePath(),
                                "Referenced user asset is not a regular file"));
                        continue;
                    }
                    validAssets.add(new ValidatedUserAsset(
                            blockId,
                            material.stableId(),
                            reference,
                            realCandidate));
                } catch (InvalidPathException exception) {
                    diagnostics.add(assetDiagnostic(
                            DiagnosticSeverity.ERROR,
                            ASSET_UNSAFE,
                            blockId,
                            reference.projectRelativePath(),
                            "Referenced user asset path is invalid on this platform"));
                } catch (IOException | SecurityException exception) {
                    diagnostics.add(assetDiagnostic(
                            DiagnosticSeverity.ERROR,
                            ASSET_IO_FAILURE,
                            blockId,
                            reference.projectRelativePath(),
                            "Referenced user asset could not be inspected"));
                }
            }
        }
        return new AssetValidationResult(validAssets, diagnostics);
    }

    private static Diagnostic assetDiagnostic(
            DiagnosticSeverity severity,
            CanonicalIdentifier code,
            CanonicalIdentifier blockId,
            String path,
            String message) {
        return diagnostic(severity, code, message + ": " + path + " (" + blockId + ")");
    }

    private static Diagnostic diagnostic(
            DiagnosticSeverity severity,
            CanonicalIdentifier code,
            String message) {
        return new Diagnostic(severity, code, message, Optional.empty(), Map.of());
    }
}
