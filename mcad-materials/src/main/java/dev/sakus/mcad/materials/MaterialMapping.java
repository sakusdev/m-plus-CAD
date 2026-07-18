/*
 * SPDX-License-Identifier: MPL-2.0
 */
package dev.sakus.mcad.materials;

import dev.sakus.mcad.api.AlphaMode;
import dev.sakus.mcad.api.Color3d;
import dev.sakus.mcad.api.Color4d;
import dev.sakus.mcad.api.MaterialDefinition;
import dev.sakus.mcad.api.UserAssetReference;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.OptionalDouble;

/** Serializable project material entry which converts directly to the neutral API definition. */
public record MaterialMapping(
        String stableId,
        String name,
        Color4d baseColour,
        double metallic,
        double roughness,
        Color3d emissiveColour,
        double emissiveStrength,
        AlphaMode alphaMode,
        OptionalDouble alphaCutoff,
        List<UserAssetReference> externalUserAssetReferences) {

    public MaterialMapping {
        Objects.requireNonNull(alphaCutoff, "alphaCutoff");
        Objects.requireNonNull(externalUserAssetReferences, "externalUserAssetReferences");
        var assets = new ArrayList<UserAssetReference>(externalUserAssetReferences);
        assets.sort(UserAssetReference::compareTo);
        for (int index = 1; index < assets.size(); index++) {
            if (assets.get(index - 1).equals(assets.get(index))) {
                throw new IllegalArgumentException("externalUserAssetReferences must not contain duplicates");
            }
        }
        externalUserAssetReferences = List.copyOf(assets);

        // Reuse the stable neutral API validation instead of maintaining a divergent copy.
        new MaterialDefinition(
                stableId,
                name,
                baseColour,
                metallic,
                roughness,
                emissiveColour,
                emissiveStrength,
                alphaMode,
                alphaCutoff,
                externalUserAssetReferences,
                Map.of(),
                List.of());
    }

    public MaterialDefinition toDefinition() {
        return new MaterialDefinition(
                stableId,
                name,
                baseColour,
                metallic,
                roughness,
                emissiveColour,
                emissiveStrength,
                alphaMode,
                alphaCutoff,
                externalUserAssetReferences,
                Map.of(),
                List.of());
    }
}
