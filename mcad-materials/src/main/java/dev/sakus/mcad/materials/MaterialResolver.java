/*
 * SPDX-License-Identifier: MPL-2.0
 */
package dev.sakus.mcad.materials;

import dev.sakus.mcad.api.CanonicalIdentifier;
import dev.sakus.mcad.api.Diagnostic;
import dev.sakus.mcad.api.DiagnosticSeverity;
import dev.sakus.mcad.api.MaterialDefinition;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Resolves materials without reading a Minecraft world or any filesystem asset. */
public final class MaterialResolver {
    private static final CanonicalIdentifier BUILT_IN_MISSING =
            CanonicalIdentifier.parse("mcad:material_builtin_missing");
    private static final CanonicalIdentifier USER_MAPPING_MISSING =
            CanonicalIdentifier.parse("mcad:material_mapping_missing");

    private final MaterialMappingDocument userMappings;

    public MaterialResolver() {
        this(MaterialMappingDocument.empty());
    }

    public MaterialResolver(MaterialMappingDocument userMappings) {
        this.userMappings = Objects.requireNonNull(userMappings, "userMappings");
    }

    public MaterialResolution resolve(CanonicalIdentifier blockId, MaterialMode mode) {
        Objects.requireNonNull(blockId, "blockId");
        Objects.requireNonNull(mode, "mode");
        return switch (mode) {
            case NONE -> new MaterialResolution(
                    Optional.empty(), Optional.empty(), MaterialResolutionSource.NONE, List.of());
            case BUILT_IN_SINGLE_COLOUR -> resolveBuiltIn(blockId);
            case VERTEX_COLOUR -> new MaterialResolution(
                    Optional.empty(),
                    Optional.of(IdentificationColours.forBlock(blockId)),
                    MaterialResolutionSource.VERTEX_COLOUR,
                    List.of());
            case IDENTIFICATION_COLOUR -> materialResolution(
                    IdentificationColours.materialFor(blockId).toDefinition(),
                    MaterialResolutionSource.IDENTIFICATION,
                    List.of());
            case USER_MAPPING -> resolveUserMapping(blockId);
        };
    }

    private MaterialResolution resolveBuiltIn(CanonicalIdentifier blockId) {
        Optional<MaterialMapping> mapping = BuiltInMaterialTable.lookup(blockId);
        if (mapping.isPresent()) {
            return materialResolution(mapping.orElseThrow().toDefinition(), MaterialResolutionSource.BUILT_IN, List.of());
        }
        return fallback(
                blockId,
                new Diagnostic(
                        DiagnosticSeverity.WARNING,
                        BUILT_IN_MISSING,
                        "No built-in m+CAD material exists for " + blockId + "; using deterministic identification colour",
                        Optional.empty(),
                        Map.of()));
    }

    private MaterialResolution resolveUserMapping(CanonicalIdentifier blockId) {
        MaterialMapping mapping = userMappings.mappings().get(blockId);
        if (mapping != null) {
            return materialResolution(mapping.toDefinition(), MaterialResolutionSource.USER_MAPPING, List.of());
        }
        return fallback(
                blockId,
                new Diagnostic(
                        DiagnosticSeverity.WARNING,
                        USER_MAPPING_MISSING,
                        "No user material mapping exists for " + blockId + "; using deterministic identification colour",
                        Optional.empty(),
                        Map.of()));
    }

    private static MaterialResolution fallback(CanonicalIdentifier blockId, Diagnostic diagnostic) {
        return materialResolution(
                IdentificationColours.materialFor(blockId).toDefinition(),
                MaterialResolutionSource.IDENTIFICATION_FALLBACK,
                List.of(diagnostic));
    }

    private static MaterialResolution materialResolution(
            MaterialDefinition definition,
            MaterialResolutionSource source,
            List<Diagnostic> diagnostics) {
        return new MaterialResolution(Optional.of(definition), Optional.empty(), source, diagnostics);
    }
}
