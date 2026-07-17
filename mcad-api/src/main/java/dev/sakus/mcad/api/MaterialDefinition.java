/*
 * SPDX-License-Identifier: MPL-2.0
 */
package dev.sakus.mcad.api;


import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.OptionalDouble;

public record MaterialDefinition(
        String stableId,
        String name,
        Color4d baseColour,
        double metallic,
        double roughness,
        Color3d emissiveColour,
        double emissiveStrength,
        AlphaMode alphaMode,
        OptionalDouble alphaCutoff,
        List<UserAssetReference> externalUserAssetReferences,
        NavigableMap<CanonicalIdentifier, MetadataValue> customProperties) {

    public MaterialDefinition(
            String stableId,
            String name,
            Color4d baseColour,
            double metallic,
            double roughness,
            Color3d emissiveColour,
            double emissiveStrength,
            AlphaMode alphaMode,
            OptionalDouble alphaCutoff,
            List<UserAssetReference> externalUserAssetReferences,
            Map<CanonicalIdentifier, MetadataValue> customProperties) {
        this(stableId, name, baseColour, metallic, roughness, emissiveColour, emissiveStrength,
                alphaMode, alphaCutoff, externalUserAssetReferences,
                Checks.immutableSortedMap(customProperties, CanonicalIdentifier::compareTo, "customProperties"));
    }

    public MaterialDefinition {
        Checks.stableId(stableId, "stableId");
        Checks.nonBlank(name, "name");
        Checks.notNull(baseColour, "baseColour");
        Checks.range(metallic, 0.0, 1.0, "metallic");
        Checks.range(roughness, 0.0, 1.0, "roughness");
        Checks.notNull(emissiveColour, "emissiveColour");
        Checks.finite(emissiveStrength, "emissiveStrength");
        if (emissiveStrength < 0.0) {
            throw new IllegalArgumentException("emissiveStrength must be non-negative");
        }
        Checks.notNull(alphaMode, "alphaMode");
        alphaCutoff = Checks.notNull(alphaCutoff, "alphaCutoff");
        if (alphaCutoff.isPresent()) {
            Checks.range(alphaCutoff.getAsDouble(), 0.0, 1.0, "alphaCutoff");
        }
        if (alphaMode != AlphaMode.MASK && alphaCutoff.isPresent()) {
            throw new IllegalArgumentException("alphaCutoff is only valid for MASK materials");
        }
        externalUserAssetReferences = Checks.immutableSortedList(
                externalUserAssetReferences, UserAssetReference::compareTo, "externalUserAssetReferences");
        Checks.requireNoDuplicates(externalUserAssetReferences, "externalUserAssetReferences");
        customProperties = Checks.immutableSortedMap(
                customProperties, CanonicalIdentifier::compareTo, "customProperties");
    }
}
