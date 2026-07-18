/*
 * SPDX-License-Identifier: MPL-2.0
 */
package dev.sakus.mcad.minecraft.runtime;

import dev.sakus.mcad.minecraft.selection.SelectionOverlayModel;
import dev.sakus.mcad.minecraft.selection.SelectionWireframe;

import java.util.Objects;
import net.minecraft.client.Minecraft;
import net.minecraft.core.particles.ParticleTypes;

/** Lightweight world-space selection preview compatible with the 26.2 render-state pipeline. */
public final class SelectionParticleOverlay {
    private static final int TICK_INTERVAL = 4;
    private static final int MAX_SAMPLES_PER_EDGE = 32;
    private int ticks;

    public void tick(Minecraft minecraft, SelectionOverlayModel model, boolean enabled) {
        Objects.requireNonNull(minecraft, "minecraft");
        Objects.requireNonNull(model, "model");
        ticks++;
        if (!enabled || minecraft.level == null || model.wireframe().isEmpty() || ticks % TICK_INTERVAL != 0) {
            return;
        }
        for (SelectionWireframe.Segment segment : model.wireframe().orElseThrow().segments()) {
            emitSegment(minecraft, segment);
        }
    }

    private static void emitSegment(Minecraft minecraft, SelectionWireframe.Segment segment) {
        double dx = segment.end().x() - segment.start().x();
        double dy = segment.end().y() - segment.start().y();
        double dz = segment.end().z() - segment.start().z();
        double length = Math.sqrt(dx * dx + dy * dy + dz * dz);
        int samples = Math.max(2, Math.min(MAX_SAMPLES_PER_EDGE, (int) Math.ceil(length * 2.0) + 1));
        for (int index = 0; index < samples; index++) {
            double t = samples == 1 ? 0.0 : (double) index / (samples - 1);
            minecraft.level.addParticle(
                    ParticleTypes.ELECTRIC_SPARK,
                    segment.start().x() + dx * t,
                    segment.start().y() + dy * t,
                    segment.start().z() + dz * t,
                    0.0,
                    0.0,
                    0.0);
        }
    }
}
