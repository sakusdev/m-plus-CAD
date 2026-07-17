/*
 * SPDX-License-Identifier: MPL-2.0
 */
package dev.sakus.mcad.minecraft.gui.settings;

import dev.sakus.mcad.api.CanonicalIdentifier;
import dev.sakus.mcad.api.ExporterCapabilities;
import dev.sakus.mcad.api.ProjectSettings;
import dev.sakus.mcad.minecraft.gui.ExporterCapabilityModel;
import dev.sakus.mcad.minecraft.gui.SettingsSection;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.UnaryOperator;

/**
 * Platform-neutral state controller for the Minecraft settings screen shell.
 */
public final class SettingsShellController {
    @FunctionalInterface
    public interface Listener {
        void shellChanged(Snapshot snapshot);
    }

    @FunctionalInterface
    public interface Subscription extends AutoCloseable {
        @Override
        void close();
    }

    public record Snapshot(
            SettingsSection activeSection,
            ProjectSettingsDraft draft,
            Optional<ExporterCapabilityModel> exporterCapabilities) {
        public Snapshot {
            Objects.requireNonNull(activeSection, "activeSection");
            Objects.requireNonNull(draft, "draft");
            exporterCapabilities = Objects.requireNonNull(exporterCapabilities, "exporterCapabilities");
        }
    }

    private final Object lock = new Object();
    private final CopyOnWriteArrayList<Listener> listeners = new CopyOnWriteArrayList<>();
    private SettingsSection activeSection = SettingsSection.SELECTION;
    private ProjectSettingsDraft draft;
    private Optional<ExporterCapabilityModel> exporterCapabilities = Optional.empty();

    public SettingsShellController(ProjectSettings initialSettings) {
        draft = ProjectSettingsDraft.from(Objects.requireNonNull(initialSettings, "initialSettings"));
    }

    public Snapshot snapshot() {
        synchronized (lock) {
            return snapshotLocked();
        }
    }

    public Snapshot selectSection(SettingsSection section) {
        Objects.requireNonNull(section, "section");
        Snapshot updated;
        synchronized (lock) {
            if (activeSection == section) {
                return snapshotLocked();
            }
            activeSection = section;
            updated = snapshotLocked();
        }
        publish(updated);
        return updated;
    }

    public Snapshot edit(UnaryOperator<ProjectSettingsDraft> editor) {
        Objects.requireNonNull(editor, "editor");
        Snapshot updated;
        synchronized (lock) {
            ProjectSettingsDraft next = Objects.requireNonNull(editor.apply(draft), "edited draft");
            if (next.equals(draft)) {
                return snapshotLocked();
            }
            draft = next;
            updated = snapshotLocked();
        }
        publish(updated);
        return updated;
    }

    public Snapshot selectExporter(CanonicalIdentifier exporterId, ExporterCapabilities capabilities) {
        Objects.requireNonNull(exporterId, "exporterId");
        ExporterCapabilityModel capabilityModel = ExporterCapabilityModel.from(capabilities);
        Snapshot updated;
        synchronized (lock) {
            draft = draft.withExporterId(exporterId);
            exporterCapabilities = Optional.of(capabilityModel);
            updated = snapshotLocked();
        }
        publish(updated);
        return updated;
    }

    public Snapshot clearExporterCapabilities() {
        Snapshot updated;
        synchronized (lock) {
            if (exporterCapabilities.isEmpty()) {
                return snapshotLocked();
            }
            exporterCapabilities = Optional.empty();
            updated = snapshotLocked();
        }
        publish(updated);
        return updated;
    }

    public Path save(ProjectSettingsStore store, Path relativePath) throws IOException {
        Objects.requireNonNull(store, "store");
        ProjectSettings settings;
        synchronized (lock) {
            settings = draft.value();
        }
        return store.save(relativePath, settings);
    }

    public DecodedProjectSettings load(
            ProjectSettingsStore store,
            Path relativePath,
            ProjectSettings migrationDefaults) throws IOException {
        Objects.requireNonNull(store, "store");
        DecodedProjectSettings decoded = store.load(relativePath, migrationDefaults);
        Snapshot updated;
        synchronized (lock) {
            draft = ProjectSettingsDraft.from(decoded.settings());
            exporterCapabilities = Optional.empty();
            updated = snapshotLocked();
        }
        publish(updated);
        return decoded;
    }

    public Subscription subscribe(Listener listener) {
        Listener checked = Objects.requireNonNull(listener, "listener");
        listeners.add(checked);
        return () -> listeners.remove(checked);
    }

    private Snapshot snapshotLocked() {
        return new Snapshot(activeSection, draft, exporterCapabilities);
    }

    private void publish(Snapshot updated) {
        for (Listener listener : listeners) {
            listener.shellChanged(updated);
        }
    }
}
