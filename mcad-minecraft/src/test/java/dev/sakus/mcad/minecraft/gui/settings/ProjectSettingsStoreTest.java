/*
 * SPDX-License-Identifier: MPL-2.0
 */
package dev.sakus.mcad.minecraft.gui.settings;

import dev.sakus.mcad.api.ProjectSettings;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class ProjectSettingsStoreTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void savesAndLoadsThroughAtomicTemporaryReplacement() throws IOException {
        ProjectSettings settings = ProjectSettingsFixtures.populated();
        ProjectSettingsStore store = new ProjectSettingsStore(
                temporaryDirectory,
                new ProjectSettingsCodec());

        Path saved = store.save(Path.of("profiles", "default.mcad-settings"), settings);
        DecodedProjectSettings loaded = store.load(
                Path.of("profiles", "default.mcad-settings"),
                ProjectSettingsFixtures.migrationDefaults());

        assertEquals(settings, loaded.settings());
        assertEquals(temporaryDirectory.resolve("profiles/default.mcad-settings"), saved);
        try (var files = Files.list(saved.getParent())) {
            assertFalse(files.anyMatch(path -> path.getFileName().toString().endsWith(".tmp")));
        }
    }

    @Test
    void rejectsAbsoluteTraversalAndNonPortablePaths() throws IOException {
        ProjectSettingsStore store = new ProjectSettingsStore(
                temporaryDirectory,
                new ProjectSettingsCodec());
        ProjectSettings settings = ProjectSettingsFixtures.populated();

        assertThrows(IllegalArgumentException.class,
                () -> store.save(Path.of("..", "escape.mcad-settings"), settings));
        assertThrows(IllegalArgumentException.class,
                () -> store.save(Path.of("profiles", "..", "escape.mcad-settings"), settings));
        assertThrows(IllegalArgumentException.class,
                () -> store.save(Path.of("profiles\\escape.mcad-settings"), settings));
        assertThrows(IllegalArgumentException.class,
                () -> store.save(Path.of("C:escape.mcad-settings"), settings));
        assertThrows(IllegalArgumentException.class,
                () -> store.save(temporaryDirectory.resolve("absolute.mcad-settings"), settings));
    }

    @Test
    void rejectsRegularFileAsDirectoryComponent() throws IOException {
        Files.writeString(temporaryDirectory.resolve("profiles"), "not a directory");
        ProjectSettingsStore store = new ProjectSettingsStore(
                temporaryDirectory,
                new ProjectSettingsCodec());

        assertThrows(IOException.class, () -> store.save(
                Path.of("profiles", "default.mcad-settings"),
                ProjectSettingsFixtures.populated()));
    }

    @Test
    void rejectsOversizedSettingsFilesBeforeDecoding() throws IOException {
        Path oversized = temporaryDirectory.resolve("oversized.mcad-settings");
        try (var output = Files.newOutputStream(oversized)) {
            output.write(new byte[16 * 1024 * 1024]);
            output.write(0);
        }
        ProjectSettingsStore store = new ProjectSettingsStore(
                temporaryDirectory,
                new ProjectSettingsCodec());

        assertThrows(IOException.class, () -> store.load(
                Path.of("oversized.mcad-settings"),
                ProjectSettingsFixtures.migrationDefaults()));
    }

    @Test
    void rejectsSymbolicLinkPathComponentsWhenSupported() throws IOException {
        Path outside = Files.createTempDirectory("mcad-settings-outside-");
        Path link = temporaryDirectory.resolve("linked");
        try {
            Files.createSymbolicLink(link, outside);
        } catch (UnsupportedOperationException | IOException | SecurityException exception) {
            assumeTrue(false, "symbolic links are unavailable: " + exception.getMessage());
            return;
        }

        ProjectSettingsStore store = new ProjectSettingsStore(
                temporaryDirectory,
                new ProjectSettingsCodec());
        assertThrows(IOException.class, () -> store.save(
                Path.of("linked", "escape.mcad-settings"),
                ProjectSettingsFixtures.populated()));
    }
}
