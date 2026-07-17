/*
 * SPDX-License-Identifier: MPL-2.0
 */
package dev.sakus.mcad.minecraft.gui;

import dev.sakus.mcad.api.ApiVersions;
import dev.sakus.mcad.api.ExporterCapabilities;
import dev.sakus.mcad.api.ExporterFeature;
import dev.sakus.mcad.api.ExporterLimits;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExporterCapabilityModelTest {
    @Test
    void mapsEveryExporterFeatureExactlyOnce() {
        EnumSet<ExporterFeature> mapped = EnumSet.noneOf(ExporterFeature.class);
        for (ExporterCapabilityModel.Control control : ExporterCapabilityModel.Control.values()) {
            assertTrue(mapped.add(control.requiredFeature()),
                    () -> "duplicate feature mapping: " + control.requiredFeature());
        }
        assertEquals(EnumSet.allOf(ExporterFeature.class), mapped);
    }

    @Test
    void disablesUnsupportedControlsWithExplanation() {
        ExporterCapabilities capabilities = new ExporterCapabilities(
                ApiVersions.GENERATED_SCENE,
                ApiVersions.GENERATED_SCENE,
                Set.of(ExporterFeature.MULTIPLE_MESHES, ExporterFeature.VERTEX_COLOURS),
                ExporterLimits.unlimited(),
                Set.of("obj"));

        ExporterCapabilityModel model = ExporterCapabilityModel.from(capabilities);

        assertTrue(model.availability(ExporterCapabilityModel.Control.MULTIPLE_MESHES).enabled());
        assertTrue(model.availability(ExporterCapabilityModel.Control.VERTEX_COLOURS).enabled());
        ControlAvailability animation = model.availability(ExporterCapabilityModel.Control.ANIMATION);
        assertFalse(animation.enabled());
        assertFalse(animation.explanation().isBlank());
    }

    @Test
    void controlMapCannotBeModifiedExternally() {
        ExporterCapabilities capabilities = new ExporterCapabilities(
                ApiVersions.GENERATED_SCENE,
                ApiVersions.GENERATED_SCENE,
                Set.of(),
                ExporterLimits.unlimited(),
                Set.of("obj"));
        ExporterCapabilityModel model = ExporterCapabilityModel.from(capabilities);

        assertThrows(UnsupportedOperationException.class, () -> model.controls().clear());
    }
}
