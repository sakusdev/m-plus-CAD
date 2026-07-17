/*
 * SPDX-License-Identifier: MPL-2.0
 */
package dev.sakus.mcad.materials;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.sakus.mcad.api.AlphaMode;
import dev.sakus.mcad.api.CanonicalIdentifier;
import dev.sakus.mcad.api.Color3d;
import dev.sakus.mcad.api.Color4d;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;
import org.junit.jupiter.api.Test;

class MaterialResolverTest {
    @Test
    void resolvesKnownAndUnknownBlockIdsDeterministically() {
        MaterialResolver resolver = new MaterialResolver();
        CanonicalIdentifier stone = CanonicalIdentifier.parse("minecraft:stone");
        CanonicalIdentifier unknown = CanonicalIdentifier.parse("example:unknown_block");

        MaterialResolution known = resolver.resolve(stone, MaterialMode.BUILT_IN_SINGLE_COLOUR);
        assertEquals(MaterialResolutionSource.BUILT_IN, known.source());
        assertTrue(known.material().isPresent());
        assertTrue(known.diagnostics().isEmpty());

        MaterialResolution firstFallback = resolver.resolve(unknown, MaterialMode.BUILT_IN_SINGLE_COLOUR);
        MaterialResolution secondFallback = resolver.resolve(unknown, MaterialMode.BUILT_IN_SINGLE_COLOUR);
        assertEquals(firstFallback, secondFallback);
        assertEquals(MaterialResolutionSource.IDENTIFICATION_FALLBACK, firstFallback.source());
        assertEquals(1, firstFallback.diagnostics().size());
        assertNotEquals(
                known.material().orElseThrow().baseColour(),
                firstFallback.material().orElseThrow().baseColour());
    }

    @Test
    void producesVertexColourDataWithoutCreatingAMaterial() {
        MaterialResolution result = new MaterialResolver().resolve(
                CanonicalIdentifier.parse("example:vertex_block"),
                MaterialMode.VERTEX_COLOUR);

        assertTrue(result.material().isEmpty());
        assertTrue(result.vertexColour().isPresent());
        assertEquals(MaterialResolutionSource.VERTEX_COLOUR, result.source());
    }

    @Test
    void userMappingWinsAndMissingMappingUsesVisibleFallback() {
        CanonicalIdentifier mappedId = CanonicalIdentifier.parse("example:mapped");
        MaterialMapping mapping = material("user.material.mapped", new Color4d(0.1, 0.2, 0.3, 1.0));
        MaterialResolver resolver = new MaterialResolver(new MaterialMappingDocument(
                MaterialMappingDocument.CURRENT_SCHEMA_VERSION,
                Map.of(mappedId, mapping)));

        MaterialResolution mapped = resolver.resolve(mappedId, MaterialMode.USER_MAPPING);
        assertEquals(MaterialResolutionSource.USER_MAPPING, mapped.source());
        assertEquals("user.material.mapped", mapped.material().orElseThrow().stableId());
        assertTrue(mapped.diagnostics().isEmpty());

        MaterialResolution missing = resolver.resolve(
                CanonicalIdentifier.parse("example:not_mapped"),
                MaterialMode.USER_MAPPING);
        assertEquals(MaterialResolutionSource.IDENTIFICATION_FALLBACK, missing.source());
        assertFalse(missing.diagnostics().isEmpty());
    }

    @Test
    void rejectsInvalidPbrValues() {
        assertThrows(IllegalArgumentException.class, () -> new MaterialMapping(
                "bad.material",
                "Bad",
                new Color4d(0.0, 0.0, 0.0, 1.0),
                Double.NaN,
                0.5,
                Color3d.BLACK,
                0.0,
                AlphaMode.OPAQUE,
                OptionalDouble.empty(),
                List.of()));
        assertThrows(IllegalArgumentException.class, () -> new MaterialMapping(
                "bad.material",
                "Bad",
                new Color4d(0.0, 0.0, 0.0, 1.0),
                0.0,
                1.01,
                Color3d.BLACK,
                0.0,
                AlphaMode.OPAQUE,
                OptionalDouble.empty(),
                List.of()));
    }

    private static MaterialMapping material(String stableId, Color4d colour) {
        return new MaterialMapping(
                stableId,
                "Mapped",
                colour,
                0.0,
                0.5,
                Color3d.BLACK,
                0.0,
                AlphaMode.OPAQUE,
                OptionalDouble.empty(),
                List.of());
    }
}
