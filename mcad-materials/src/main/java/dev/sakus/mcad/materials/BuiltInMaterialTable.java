/*
 * SPDX-License-Identifier: MPL-2.0
 */
package dev.sakus.mcad.materials;

import dev.sakus.mcad.api.AlphaMode;
import dev.sakus.mcad.api.CanonicalIdentifier;
import dev.sakus.mcad.api.Color3d;
import dev.sakus.mcad.api.Color4d;
import java.util.Collections;
import java.util.List;
import java.util.NavigableMap;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.TreeMap;

/** Independently authored m+CAD single-colour defaults. No values are sampled from Minecraft assets. */
public final class BuiltInMaterialTable {
    private static final NavigableMap<CanonicalIdentifier, MaterialMapping> MAPPINGS = createMappings();

    private BuiltInMaterialTable() {
    }

    public static Optional<MaterialMapping> lookup(CanonicalIdentifier blockId) {
        return Optional.ofNullable(MAPPINGS.get(blockId));
    }

    public static NavigableMap<CanonicalIdentifier, MaterialMapping> mappings() {
        return MAPPINGS;
    }

    private static NavigableMap<CanonicalIdentifier, MaterialMapping> createMappings() {
        var values = new TreeMap<CanonicalIdentifier, MaterialMapping>();
        add(values, "minecraft:stone", "Stone", 0x777B80, 0.0, 0.90);
        add(values, "minecraft:cobblestone", "Cobblestone", 0x686D73, 0.0, 0.95);
        add(values, "minecraft:dirt", "Earth", 0x75513A, 0.0, 1.00);
        add(values, "minecraft:grass_block", "Grass", 0x5B7F43, 0.0, 0.95);
        add(values, "minecraft:sand", "Sand", 0xD6C58D, 0.0, 1.00);
        add(values, "minecraft:gravel", "Gravel", 0x77706D, 0.0, 1.00);
        add(values, "minecraft:oak_planks", "Oak", 0xA77745, 0.0, 0.82);
        add(values, "minecraft:spruce_planks", "Spruce", 0x6E4C31, 0.0, 0.86);
        add(values, "minecraft:birch_planks", "Birch", 0xCEB77B, 0.0, 0.80);
        add(values, "minecraft:bricks", "Brick", 0x965449, 0.0, 0.88);
        add(values, "minecraft:white_wool", "White Wool", 0xD9DADC, 0.0, 1.00);
        add(values, "minecraft:black_wool", "Black Wool", 0x24272B, 0.0, 1.00);
        add(values, "minecraft:coal_block", "Coal", 0x25272A, 0.0, 0.88);
        add(values, "minecraft:iron_block", "Iron", 0xC7CCD0, 0.78, 0.32);
        add(values, "minecraft:gold_block", "Gold", 0xE0B832, 0.88, 0.27);
        add(values, "minecraft:diamond_block", "Diamond", 0x42C7BD, 0.52, 0.25);
        add(values, "minecraft:redstone_block", "Redstone", 0xB5231E, 0.18, 0.48);
        addTransparent(values, "minecraft:glass", "Glass", 0xC8E3E6, 0.28, 0.10);
        addTransparent(values, "minecraft:water", "Water", 0x3378C8, 0.42, 0.08);
        addEmissive(values, "minecraft:lava", "Lava", 0xE85A16, 3.0);
        return Collections.unmodifiableNavigableMap(values);
    }

    private static void add(
            TreeMap<CanonicalIdentifier, MaterialMapping> values,
            String blockId,
            String name,
            int srgb,
            double metallic,
            double roughness) {
        CanonicalIdentifier id = CanonicalIdentifier.parse(blockId);
        values.put(id, new MaterialMapping(
                "mcad.material.builtin/" + id,
                "m+CAD " + name,
                linearColour(srgb, 1.0),
                metallic,
                roughness,
                Color3d.BLACK,
                0.0,
                AlphaMode.OPAQUE,
                OptionalDouble.empty(),
                List.of()));
    }

    private static void addTransparent(
            TreeMap<CanonicalIdentifier, MaterialMapping> values,
            String blockId,
            String name,
            int srgb,
            double roughness,
            double alpha) {
        CanonicalIdentifier id = CanonicalIdentifier.parse(blockId);
        values.put(id, new MaterialMapping(
                "mcad.material.builtin/" + id,
                "m+CAD " + name,
                linearColour(srgb, alpha),
                0.0,
                roughness,
                Color3d.BLACK,
                0.0,
                AlphaMode.BLEND,
                OptionalDouble.empty(),
                List.of()));
    }

    private static void addEmissive(
            TreeMap<CanonicalIdentifier, MaterialMapping> values,
            String blockId,
            String name,
            int srgb,
            double emissiveStrength) {
        CanonicalIdentifier id = CanonicalIdentifier.parse(blockId);
        Color4d base = linearColour(srgb, 1.0);
        values.put(id, new MaterialMapping(
                "mcad.material.builtin/" + id,
                "m+CAD " + name,
                base,
                0.0,
                0.62,
                new Color3d(base.red(), base.green(), base.blue()),
                emissiveStrength,
                AlphaMode.OPAQUE,
                OptionalDouble.empty(),
                List.of()));
    }

    private static Color4d linearColour(int rgb, double alpha) {
        double red = toLinear((rgb >>> 16 & 0xff) / 255.0);
        double green = toLinear((rgb >>> 8 & 0xff) / 255.0);
        double blue = toLinear((rgb & 0xff) / 255.0);
        return new Color4d(red, green, blue, alpha);
    }

    private static double toLinear(double srgb) {
        return srgb <= 0.04045 ? srgb / 12.92 : Math.pow((srgb + 0.055) / 1.055, 2.4);
    }
}
