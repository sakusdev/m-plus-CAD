/*
 * SPDX-License-Identifier: MPL-2.0
 */
package dev.sakus.mcad.minecraft.gui.settings;

import dev.sakus.mcad.api.ApiVersions;
import dev.sakus.mcad.api.CanonicalIdentifier;
import dev.sakus.mcad.api.MetadataValue;
import dev.sakus.mcad.api.ProjectSettings;
import dev.sakus.mcad.api.SchemaVersion;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProjectSettingsCodecTest {
    private final ProjectSettingsCodec codec = new ProjectSettingsCodec();

    @Test
    void roundTripsAllSettingsAndProducesDeterministicBytes() {
        ProjectSettings settings = ProjectSettingsFixtures.populated();

        byte[] first = codec.encode(settings);
        byte[] second = codec.encode(settings);
        DecodedProjectSettings decoded = codec.decode(first, ProjectSettingsFixtures.migrationDefaults());

        assertArrayEquals(first, second);
        assertEquals(settings, decoded.settings());
        assertEquals(ProjectSettingsCodec.CURRENT_STORAGE_VERSION, decoded.sourceStorageVersion());
        assertFalse(decoded.migrated());
    }

    @Test
    void migratesLegacyVersionZeroUsingDefaultsForNewSections() {
        ProjectSettings legacy = ProjectSettingsFixtures.populated();
        ProjectSettings defaults = ProjectSettingsFixtures.migrationDefaults();
        byte[] document = ProjectSettingsCodec.encodeLegacyVersionZero(legacy);

        DecodedProjectSettings decoded = codec.decode(document, defaults);
        ProjectSettings expected = ProjectSettingsDraft.from(legacy)
                .withAnimation(defaults.animation())
                .withPreview(defaults.preview())
                .value();

        assertEquals(expected, decoded.settings());
        assertEquals(ApiVersions.PROJECT_SETTINGS, decoded.settings().schemaVersion());
        assertEquals(0, decoded.sourceStorageVersion());
        assertTrue(decoded.migrated());
    }

    @Test
    void rejectsUnknownStorageVersionTrailingDataAndTruncation() {
        byte[] valid = codec.encode(ProjectSettingsFixtures.populated());
        byte[] unknownVersion = valid.clone();
        ByteBuffer.wrap(unknownVersion).putInt(4, 99);
        byte[] trailing = Arrays.copyOf(valid, valid.length + 1);
        byte[] truncated = Arrays.copyOf(valid, valid.length - 1);

        assertThrows(IllegalArgumentException.class,
                () -> codec.decode(unknownVersion, ProjectSettingsFixtures.migrationDefaults()));
        assertThrows(IllegalArgumentException.class,
                () -> codec.decode(trailing, ProjectSettingsFixtures.migrationDefaults()));
        assertThrows(IllegalArgumentException.class,
                () -> codec.decode(truncated, ProjectSettingsFixtures.migrationDefaults()));
    }

    @Test
    void rejectsFutureProjectSettingsSchemaForEncodeDecodeAndDefaults() {
        ProjectSettings current = ProjectSettingsFixtures.populated();
        ProjectSettings future = withSchema(current, new SchemaVersion(2, 0));
        byte[] futureDocument = codec.encode(current);
        ByteBuffer.wrap(futureDocument).putInt(8, 2);

        assertThrows(IllegalArgumentException.class, () -> codec.encode(future));
        assertThrows(IllegalArgumentException.class,
                () -> codec.decode(futureDocument, ProjectSettingsFixtures.migrationDefaults()));
        assertThrows(IllegalArgumentException.class,
                () -> codec.decode(codec.encode(current), future));
    }

    @Test
    void rejectsMalformedUtf8AndUnpairedSurrogates() {
        byte[] malformed = codec.encode(ProjectSettingsFixtures.populated());
        byte[] destination = "exports/model.glb".getBytes(StandardCharsets.UTF_8);
        int destinationOffset = indexOf(malformed, destination);
        assertTrue(destinationOffset >= 0);
        malformed[destinationOffset] = (byte) 0xC3;
        malformed[destinationOffset + 1] = 0x28;

        assertThrows(IllegalArgumentException.class,
                () -> codec.decode(malformed, ProjectSettingsFixtures.migrationDefaults()));

        ProjectSettings source = ProjectSettingsFixtures.populated();
        ProjectSettings.OutputSettings invalidOutput = new ProjectSettings.OutputSettings(
                source.output().exporterId(),
                "bad" + (char) 0xD800,
                source.output().lossPolicy(),
                source.output().exporterOptions());
        ProjectSettings invalidUnicode = ProjectSettingsDraft.from(source).withOutput(invalidOutput).value();
        assertThrows(IllegalArgumentException.class, () -> codec.encode(invalidUnicode));
    }

    @Test
    void rejectsMetadataNestingBeyondLimit() {
        MetadataValue value = new MetadataValue.StringValue("leaf");
        for (int depth = 0; depth < 66; depth++) {
            value = new MetadataValue.ListValue(List.of(value));
        }
        ProjectSettings source = ProjectSettingsFixtures.populated();
        ProjectSettings.OutputSettings output = new ProjectSettings.OutputSettings(
                source.output().exporterId(),
                source.output().destination(),
                source.output().lossPolicy(),
                Map.of(new CanonicalIdentifier("mcad", "deep"), value));
        ProjectSettings tooDeep = ProjectSettingsDraft.from(source).withOutput(output).value();

        assertThrows(IllegalArgumentException.class, () -> codec.encode(tooDeep));
    }

    private static ProjectSettings withSchema(ProjectSettings source, SchemaVersion schemaVersion) {
        return new ProjectSettings(
                schemaVersion,
                source.selection(),
                source.geometry(),
                source.meshSeparation(),
                source.materials(),
                source.transform(),
                source.markers(),
                source.optimization(),
                source.animation(),
                source.collision(),
                source.output(),
                source.preview(),
                source.advanced());
    }

    private static int indexOf(byte[] source, byte[] target) {
        for (int start = 0; start <= source.length - target.length; start++) {
            int index = 0;
            while (index < target.length && source[start + index] == target[index]) {
                index++;
            }
            if (index == target.length) {
                return start;
            }
        }
        return -1;
    }
}
