/*
 * SPDX-License-Identifier: MPL-2.0
 */
package dev.sakus.mcad.minecraft.gui;

import org.junit.jupiter.api.Test;

import java.util.EnumSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SettingsShellSchemaTest {
    @Test
    void everySectionHasFieldsAndEveryFieldIsAssignedExactlyOnce() {
        assertEquals(SettingsSection.ordered().size(), SettingsShellSchema.sections().size());
        EnumSet<SettingsField> fields = EnumSet.noneOf(SettingsField.class);

        for (int index = 0; index < SettingsSection.ordered().size(); index++) {
            SettingsSection expectedSection = SettingsSection.ordered().get(index);
            SettingsShellSchema.Section section = SettingsShellSchema.sections().get(index);
            assertEquals(expectedSection, section.section());
            assertFalse(section.fields().isEmpty());
            for (SettingsField field : section.fields()) {
                assertEquals(expectedSection, field.section());
                fields.add(field);
            }
        }

        assertEquals(EnumSet.allOf(SettingsField.class), fields);
    }

    @Test
    void exposesConcreteFieldsForEveryRequiredGuiArea() {
        assertEquals(
                EnumSet.of(
                        SettingsField.SELECTION_START,
                        SettingsField.SELECTION_END,
                        SettingsField.SELECTION_MAXIMUM_BLOCK_COUNT,
                        SettingsField.SELECTION_PRESERVE_EMPTY_CELLS),
                EnumSet.copyOf(SettingsShellSchema.section(SettingsSection.SELECTION).fields()));
        assertEquals(
                EnumSet.of(
                        SettingsField.OUTPUT_EXPORTER,
                        SettingsField.OUTPUT_DESTINATION,
                        SettingsField.OUTPUT_LOSS_POLICY,
                        SettingsField.OUTPUT_EXPORTER_OPTIONS),
                EnumSet.copyOf(SettingsShellSchema.section(SettingsSection.OUTPUT).fields()));
    }

    @Test
    void schemaCollectionsCannotBeModifiedExternally() {
        assertThrows(UnsupportedOperationException.class, () -> SettingsShellSchema.sections().clear());
        assertThrows(UnsupportedOperationException.class,
                () -> SettingsShellSchema.section(SettingsSection.SELECTION).fields().clear());
    }
}
