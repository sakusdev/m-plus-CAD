/*
 * SPDX-License-Identifier: MPL-2.0
 */
package dev.sakus.mcad.minecraft.gui;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;

/**
 * Immutable field layout consumed by the Minecraft settings Screen implementation.
 */
public final class SettingsShellSchema {
    public record Section(SettingsSection section, List<SettingsField> fields) {
        public Section {
            Objects.requireNonNull(section, "section");
            fields = List.copyOf(Objects.requireNonNull(fields, "fields"));
            if (fields.isEmpty()) {
                throw new IllegalArgumentException("settings section must contain at least one field");
            }
            EnumSet<SettingsField> unique = EnumSet.noneOf(SettingsField.class);
            for (SettingsField field : fields) {
                Objects.requireNonNull(field, "field");
                if (field.section() != section) {
                    throw new IllegalArgumentException("field belongs to a different section: " + field);
                }
                if (!unique.add(field)) {
                    throw new IllegalArgumentException("duplicate settings field: " + field);
                }
            }
        }
    }

    private static final List<Section> SECTIONS = createSections();

    private SettingsShellSchema() {
    }

    public static List<Section> sections() {
        return SECTIONS;
    }

    public static Section section(SettingsSection section) {
        Objects.requireNonNull(section, "section");
        return SECTIONS.get(section.ordinal());
    }

    private static List<Section> createSections() {
        List<Section> result = new ArrayList<>(SettingsSection.values().length);
        EnumSet<SettingsField> assigned = EnumSet.noneOf(SettingsField.class);
        for (SettingsSection section : SettingsSection.ordered()) {
            List<SettingsField> fields = new ArrayList<>();
            for (SettingsField field : SettingsField.values()) {
                if (field.section() == section) {
                    fields.add(field);
                    if (!assigned.add(field)) {
                        throw new IllegalStateException("settings field assigned twice: " + field);
                    }
                }
            }
            result.add(new Section(section, fields));
        }
        if (assigned.size() != SettingsField.values().length) {
            throw new IllegalStateException("not every settings field is assigned to a section");
        }
        if (result.size() != SettingsSection.values().length) {
            throw new IllegalStateException("not every settings section has a schema entry");
        }
        return List.copyOf(result);
    }
}
