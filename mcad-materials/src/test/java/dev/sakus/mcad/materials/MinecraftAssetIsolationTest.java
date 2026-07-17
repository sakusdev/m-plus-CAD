/*
 * SPDX-License-Identifier: MPL-2.0
 */
package dev.sakus.mcad.materials;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.sakus.mcad.api.CanonicalIdentifier;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class MinecraftAssetIsolationTest {
    @Test
    void builtInTableContainsNoExternalAssetReferences() {
        assertTrue(BuiltInMaterialTable.mappings().values().stream()
                .allMatch(material -> material.externalUserAssetReferences().isEmpty()));
    }

    @Test
    void resolvingDefaultsRequiresNoProjectOrMinecraftAssetPath() {
        MaterialResolver resolver = new MaterialResolver();
        assertTrue(resolver.resolve(
                        CanonicalIdentifier.parse("minecraft:stone"),
                        MaterialMode.BUILT_IN_SINGLE_COLOUR)
                .material()
                .isPresent());
        assertTrue(resolver.resolve(
                        CanonicalIdentifier.parse("example:unknown"),
                        MaterialMode.IDENTIFICATION_COLOUR)
                .material()
                .isPresent());
    }

    @Test
    void mappingParseDoesNotInspectReferencedFileUntilExplicitValidation() {
        MaterialMappingDocument document = MaterialMappingJson.read("""
                {
                  "schemaVersion": {"major": 1, "minor": 0},
                  "mappings": [
                    {
                      "blockId": "example:block",
                      "material": {
                        "stableId": "user.material.block",
                        "name": "Block",
                        "baseColour": [0.2, 0.3, 0.4, 1.0],
                        "metallic": 0.0,
                        "roughness": 0.5,
                        "emissiveColour": [0.0, 0.0, 0.0],
                        "emissiveStrength": 0.0,
                        "alphaMode": "OPAQUE",
                        "alphaCutoff": null,
                        "assets": [
                          {"projectRelativePath": "assets/not-present.png", "copyOnExport": false}
                        ]
                      }
                    }
                  ]
                }
                """);

        assertEquals(1, document.mappings().size());
        AssetValidationResult validation = ExternalAssetValidator.validate(
                Path.of("definitely-not-an-existing-project-root"),
                document);
        assertEquals("mcad:material_project_root_invalid", validation.diagnostics().get(0).code().toString());
    }
}
