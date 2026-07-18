/*
 * SPDX-License-Identifier: MPL-2.0
 */
package dev.sakus.mcad.materials;

import dev.sakus.mcad.api.AlphaMode;
import dev.sakus.mcad.api.CanonicalIdentifier;
import dev.sakus.mcad.api.Color3d;
import dev.sakus.mcad.api.Color4d;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.OptionalDouble;

/** Generates stable high-contrast identification colours from canonical block identifiers. */
public final class IdentificationColours {
    private static final long FNV_OFFSET_BASIS = 0xcbf29ce484222325L;
    private static final long FNV_PRIME = 0x100000001b3L;

    private IdentificationColours() {
    }

    public static Color4d forBlock(CanonicalIdentifier blockId) {
        long hash = hash(blockId.toString());
        double hue = Long.remainderUnsigned(hash, 360_000L) / 1000.0;
        double saturation = 0.58 + ((hash >>> 17) & 0xffL) / 255.0 * 0.22;
        double lightness = 0.48 + ((hash >>> 33) & 0xffL) / 255.0 * 0.18;
        double[] srgb = hslToSrgb(hue, saturation, lightness);
        return new Color4d(toLinear(srgb[0]), toLinear(srgb[1]), toLinear(srgb[2]), 1.0);
    }

    public static MaterialMapping materialFor(CanonicalIdentifier blockId) {
        return new MaterialMapping(
                "mcad.material.identification/" + blockId,
                "Identification " + blockId,
                forBlock(blockId),
                0.0,
                0.65,
                Color3d.BLACK,
                0.0,
                AlphaMode.OPAQUE,
                OptionalDouble.empty(),
                List.of());
    }

    private static long hash(String value) {
        long result = FNV_OFFSET_BASIS;
        for (byte item : value.getBytes(StandardCharsets.UTF_8)) {
            result ^= item & 0xffL;
            result *= FNV_PRIME;
        }
        return result;
    }

    private static double[] hslToSrgb(double hueDegrees, double saturation, double lightness) {
        double chroma = (1.0 - Math.abs(2.0 * lightness - 1.0)) * saturation;
        double sector = hueDegrees / 60.0;
        double intermediate = chroma * (1.0 - Math.abs(sector % 2.0 - 1.0));
        double red;
        double green;
        double blue;
        if (sector < 1.0) {
            red = chroma;
            green = intermediate;
            blue = 0.0;
        } else if (sector < 2.0) {
            red = intermediate;
            green = chroma;
            blue = 0.0;
        } else if (sector < 3.0) {
            red = 0.0;
            green = chroma;
            blue = intermediate;
        } else if (sector < 4.0) {
            red = 0.0;
            green = intermediate;
            blue = chroma;
        } else if (sector < 5.0) {
            red = intermediate;
            green = 0.0;
            blue = chroma;
        } else {
            red = chroma;
            green = 0.0;
            blue = intermediate;
        }
        double match = lightness - chroma / 2.0;
        return new double[] {red + match, green + match, blue + match};
    }

    private static double toLinear(double srgb) {
        return srgb <= 0.04045 ? srgb / 12.92 : Math.pow((srgb + 0.055) / 1.055, 2.4);
    }
}
