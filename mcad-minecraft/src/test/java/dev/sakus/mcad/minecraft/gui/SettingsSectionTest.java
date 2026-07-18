/*
 * SPDX-License-Identifier: MPL-2.0
 */
package dev.sakus.mcad.minecraft.gui;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SettingsSectionTest {
    @Test
    void exposesEveryContractSectionInStableOrder() {
        assertEquals(List.of(
                SettingsSection.SELECTION,
                SettingsSection.GEOMETRY,
                SettingsSection.MESH_SEPARATION,
                SettingsSection.MATERIALS,
                SettingsSection.TRANSFORM,
                SettingsSection.MARKERS,
                SettingsSection.OPTIMIZATION,
                SettingsSection.ANIMATION,
                SettingsSection.COLLISION,
                SettingsSection.OUTPUT,
                SettingsSection.PREVIEW,
                SettingsSection.ADVANCED), SettingsSection.ordered());
    }

    @Test
    void orderedSectionsCannotBeModifiedExternally() {
        assertThrows(UnsupportedOperationException.class,
                () -> SettingsSection.ordered().remove(SettingsSection.SELECTION));
    }
}
