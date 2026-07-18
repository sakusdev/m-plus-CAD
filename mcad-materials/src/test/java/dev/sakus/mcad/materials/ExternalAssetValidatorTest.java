/*
 * SPDX-License-Identifier: MPL-2.0
 */
package dev.sakus.mcad.materials;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.sakus.mcad.api.AlphaMode;
import dev.sakus.mcad.api.CanonicalIdentifier;
import dev.sakus.mcad.api.Color3d;
import dev.sakus.mcad.api.Color4d;
import dev.sakus.mcad.api.DiagnosticSeverity;
import dev.sakus.mcad.api.UserAssetReference;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ExternalAssetValidatorTest {
    @TempDir
    Path projectRoot;

    @Test
    void validatesExistingFileAndPreservesExplicitCopyFlag() throws IOException {
        Files.createDirectories(projectRoot.resolve("assets"));
        Files.writeString(projectRoot.resolve("assets/user.png"), "user-owned test content");
        MaterialMappingDocument document = documentWithAssets(List.of(
                new UserAssetReference("assets/user.png", true)));

        AssetValidationResult result = ExternalAssetValidator.validate(projectRoot, document);

        assertTrue(result.diagnostics().isEmpty());
        assertEquals(1, result.validAssets().size());
        assertTrue(result.validAssets().get(0).copyRequested());
        assertTrue(result.validAssets().get(0).resolvedPath().startsWith(projectRoot.toRealPath()));
    }

    @Test
    void missingAssetProducesStableDiagnosticWithoutCopyingAnything() {
        MaterialMappingDocument document = documentWithAssets(List.of(
                new UserAssetReference("assets/missing.png", false)));

        AssetValidationResult result = ExternalAssetValidator.validate(projectRoot, document);

        assertTrue(result.validAssets().isEmpty());
        assertEquals(1, result.diagnostics().size());
        assertEquals(DiagnosticSeverity.WARNING, result.diagnostics().get(0).severity());
        assertEquals("mcad:material_asset_missing", result.diagnostics().get(0).code().toString());
        assertFalse(Files.exists(projectRoot.resolve("assets/missing.png")));
    }

    @Test
    void directoryReferenceIsRejectedAsNotAFile() throws IOException {
        Files.createDirectories(projectRoot.resolve("assets/directory"));
        MaterialMappingDocument document = documentWithAssets(List.of(
                new UserAssetReference("assets/directory", false)));

        AssetValidationResult result = ExternalAssetValidator.validate(projectRoot, document);

        assertTrue(result.validAssets().isEmpty());
        assertEquals("mcad:material_asset_not_file", result.diagnostics().get(0).code().toString());
    }

    private static MaterialMappingDocument documentWithAssets(List<UserAssetReference> assets) {
        MaterialMapping mapping = new MaterialMapping(
                "user.material.block",
                "Block",
                new Color4d(0.2, 0.3, 0.4, 1.0),
                0.0,
                0.5,
                Color3d.BLACK,
                0.0,
                AlphaMode.OPAQUE,
                OptionalDouble.empty(),
                assets);
        return new MaterialMappingDocument(
                MaterialMappingDocument.CURRENT_SCHEMA_VERSION,
                Map.of(CanonicalIdentifier.parse("example:block"), mapping));
    }
}
