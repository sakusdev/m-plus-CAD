/*
 * SPDX-License-Identifier: MPL-2.0
 */
package dev.sakus.mcad.materials;

import dev.sakus.mcad.api.Color4d;
import dev.sakus.mcad.api.Diagnostic;
import dev.sakus.mcad.api.MaterialDefinition;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Immutable result of resolving one canonical block identifier. */
public record MaterialResolution(
        Optional<MaterialDefinition> material,
        Optional<Color4d> vertexColour,
        MaterialResolutionSource source,
        List<Diagnostic> diagnostics) {

    public MaterialResolution {
        material = Objects.requireNonNull(material, "material");
        vertexColour = Objects.requireNonNull(vertexColour, "vertexColour");
        source = Objects.requireNonNull(source, "source");
        Objects.requireNonNull(diagnostics, "diagnostics");
        if (material.isPresent() && vertexColour.isPresent()) {
            throw new IllegalArgumentException("material and vertexColour cannot both be present");
        }
        var sorted = new ArrayList<Diagnostic>(diagnostics);
        sorted.sort(Diagnostic.STABLE_ORDER);
        diagnostics = List.copyOf(sorted);
    }
}
