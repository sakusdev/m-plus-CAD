/*
 * SPDX-License-Identifier: MPL-2.0
 */
package dev.sakus.mcad.minecraft.gui.settings;

import dev.sakus.mcad.api.CanonicalIdentifier;
import dev.sakus.mcad.api.ProjectSettings;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class ProjectSettingsDraftTest {
    @Test
    void changingExporterIdPreservesEveryUnrelatedSetting() {
        ProjectSettings original = ProjectSettingsFixtures.populated();
        ProjectSettingsDraft draft = ProjectSettingsDraft.from(original)
                .withExporterId(new CanonicalIdentifier("mcad", "obj"));
        ProjectSettings updated = draft.value();

        assertEquals(1L, draft.revision());
        assertEquals(new CanonicalIdentifier("mcad", "obj"), updated.output().exporterId());
        assertEquals(original.output().destination(), updated.output().destination());
        assertEquals(original.output().lossPolicy(), updated.output().lossPolicy());
        assertEquals(original.output().exporterOptions(), updated.output().exporterOptions());
        assertEquals(original.selection(), updated.selection());
        assertEquals(original.geometry(), updated.geometry());
        assertEquals(original.meshSeparation(), updated.meshSeparation());
        assertEquals(original.materials(), updated.materials());
        assertEquals(original.transform(), updated.transform());
        assertEquals(original.markers(), updated.markers());
        assertEquals(original.optimization(), updated.optimization());
        assertEquals(original.animation(), updated.animation());
        assertEquals(original.collision(), updated.collision());
        assertEquals(original.preview(), updated.preview());
        assertEquals(original.advanced(), updated.advanced());
    }

    @Test
    void noOpEditReturnsSameDraftWithoutRevisionChange() {
        ProjectSettings original = ProjectSettingsFixtures.populated();
        ProjectSettingsDraft draft = ProjectSettingsDraft.from(original);

        assertSame(draft, draft.withSelection(original.selection()));
        assertEquals(0L, draft.revision());
    }
}
