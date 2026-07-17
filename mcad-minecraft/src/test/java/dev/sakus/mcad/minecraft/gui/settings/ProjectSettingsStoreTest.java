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
    void rejectsAbsoluteAndTraversalPaths() throws IOException {
        ProjectSettingsStore store = new ProjectSettingsStore(
                temporaryDirectory,
                new ProjectSettingsCodec());
        ProjectSettings settings = ProjectSettingsFixtures.populated();

        assertThrows(IllegalArgumentException.class,
                () -> store.save(Path.of("..", "escape.mcad-settings"), settings));
        assertThrows(IllegalArgumentException.class,
                () -> store.save(temporaryDirectory.resolve("absolute.mcad-settings"), settings));
    }

    @Test
    void rejectsSymbolicLinkPathComponentsWhenSupported() throws IOException {
        Path outside = Files.createTempDirectory("mcad-settings-outside-");
        Path link = temporaryDirectory.resolve("linked");
        try {
            Files.createSymbolicLink(link, outside);
        } catch (UnsupportedOperationException | IOException | SecurityException exception) {
            org.junit.jupiter.api.Assumptions.abort("symbolic links are unavailable: " + exception.getMessage());
        }

        ProjectSettingsStore store = new ProjectSettingsStore(
                temporaryDirectory,
                new ProjectSettingsCodec());
        assertThrows(IOException.class, () -> store.save(
                Path.of("linked", "escape.mcad-settings"),
                ProjectSettingsFixtures.populated()));
    }
}
