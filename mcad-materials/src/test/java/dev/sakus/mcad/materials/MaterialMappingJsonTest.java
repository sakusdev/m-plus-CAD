/*
 * SPDX-License-Identifier: MPL-2.0
 */
package dev.sakus.mcad.materials;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.sakus.mcad.api.AlphaMode;
import dev.sakus.mcad.api.CanonicalIdentifier;
import dev.sakus.mcad.api.Color3d;
import dev.sakus.mcad.api.Color4d;
import dev.sakus.mcad.api.UserAssetReference;
import java.util.HashMap;
import java.util.List;
import java.util.OptionalDouble;
import org.junit.jupiter.api.Test;

class MaterialMappingJsonTest {
    @Test
    void roundTripsSchemaAndUsesDeterministicBlockOrdering() {
        var mappings = new HashMap<CanonicalIdentifier, MaterialMapping>();
        mappings.put(CanonicalIdentifier.parse("zeta:last"), material("user.material.last", List.of()));
        mappings.put(CanonicalIdentifier.parse("alpha:first"), material("user.material.first", List.of()));
        MaterialMappingDocument document = new MaterialMappingDocument(
                MaterialMappingDocument.CURRENT_SCHEMA_VERSION,
                mappings);

        String serialized = MaterialMappingJson.write(document);
        MaterialMappingDocument restored = MaterialMappingJson.read(serialized);

        assertEquals(document, restored);
        assertTrue(serialized.contains("\"schemaVersion\": {\"major\": 1, \"minor\": 0}"));
        assertTrue(serialized.indexOf("alpha:first") < serialized.indexOf("zeta:last"));
        assertEquals(serialized, MaterialMappingJson.write(restored));
    }

    @Test
    void rejectsUnsafeExternalPathAndUnsupportedSchema() {
        String unsafe = json("1", "0", "../escape.png");
        assertThrows(MaterialMappingFormatException.class, () -> MaterialMappingJson.read(unsafe));

        String future = json("2", "0", "assets/user.png");
        assertThrows(MaterialMappingFormatException.class, () -> MaterialMappingJson.read(future));
    }

    @Test
    void rejectsDuplicateMappingAndDuplicateJsonKeys() {
        String one = json("1", "0", "assets/user.png");
        String materialEntry = one.substring(one.indexOf("    {"), one.lastIndexOf("\n  ]"));
        String duplicateMapping = one.replace(materialEntry, materialEntry + ",\n" + materialEntry);
        assertThrows(MaterialMappingFormatException.class, () -> MaterialMappingJson.read(duplicateMapping));

        String duplicateKey = one.replace("\"minor\": 0", "\"minor\": 0, \"minor\": 0");
        assertThrows(MaterialMappingFormatException.class, () -> MaterialMappingJson.read(duplicateKey));
    }

    private static MaterialMapping material(String stableId, List<UserAssetReference> assets) {
        return new MaterialMapping(
                stableId,
                "User Material",
                new Color4d(0.2, 0.3, 0.4, 1.0),
                0.1,
                0.6,
                Color3d.BLACK,
                0.0,
                AlphaMode.OPAQUE,
                OptionalDouble.empty(),
                assets);
    }

    private static String json(String major, String minor, String assetPath) {
        return """
                {
                  "schemaVersion": {"major": %s, "minor": %s},
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
                          {"projectRelativePath": "%s", "copyOnExport": false}
                        ]
                      }
                    }
                  ]
                }
                """.formatted(major, minor, assetPath);
    }
}
