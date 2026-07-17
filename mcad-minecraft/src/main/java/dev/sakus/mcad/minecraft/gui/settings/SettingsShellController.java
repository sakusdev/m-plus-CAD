/*
 * SPDX-License-Identifier: MPL-2.0
 */
package dev.sakus.mcad.minecraft.gui.settings;

import dev.sakus.mcad.api.CanonicalIdentifier;
import dev.sakus.mcad.api.ExporterCapabilities;
import dev.sakus.mcad.api.ProjectSettings;
import dev.sakus.mcad.minecraft.gui.ExporterCapabilityModel;
import dev.sakus.mcad.minecraft.gui.SettingsSection;
import dev.sakus.mcad.minecraft.gui.SettingsValidationModel;

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
            long revision,
            SettingsSection activeSection,
            ProjectSettingsDraft draft,
            Optional<ExporterCapabilityModel> exporterCapabilities,
            SettingsValidationModel validation) {
        public Snapshot {
            if (revision < 0L) {
                throw new IllegalArgumentException("revision must be non-negative");
            }
            Objects.requireNonNull(activeSection, "activeSection");
            Objects.requireNonNull(draft, "draft");
            exporterCapabilities = Objects.requireNonNull(exporterCapabilities, "exporterCapabilities");
            Objects.requireNonNull(validation, "validation");
        }
    }

    private final Object lock = new Object();
    private final CopyOnWriteArrayList<Listener> listeners = new CopyOnWriteArrayList<>();
    private long revision;
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
            long nextRevision = Math.incrementExact(revision);
            activeSection = section;
            revision = nextRevision;
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
            long nextRevision = Math.incrementExact(revision);
            draft = next;
            revision = nextRevision;
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
            ProjectSettingsDraft nextDraft = draft.withExporterId(exporterId);
            Optional<ExporterCapabilityModel> nextCapabilities = Optional.of(capabilityModel);
            if (nextDraft.equals(draft) && nextCapabilities.equals(exporterCapabilities)) {
                return snapshotLocked();
            }
            long nextRevision = Math.incrementExact(revision);
            draft = nextDraft;
            exporterCapabilities = nextCapabilities;
            revision = nextRevision;
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
            long nextRevision = Math.incrementExact(revision);
            exporterCapabilities = Optional.empty();
            revision = nextRevision;
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
            ProjectSettingsDraft nextDraft = ProjectSettingsDraft.from(decoded.settings());
            Optional<ExporterCapabilityModel> nextCapabilities = Optional.empty();
            if (nextDraft.equals(draft) && nextCapabilities.equals(exporterCapabilities)) {
                return decoded;
            }
            long nextRevision = Math.incrementExact(revision);
            draft = nextDraft;
            exporterCapabilities = nextCapabilities;
            revision = nextRevision;
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
        return new Snapshot(
                revision,
                activeSection,
                draft,
                exporterCapabilities,
                SettingsValidationModel.validate(draft.value(), exporterCapabilities));
    }

    private void publish(Snapshot updated) {
        for (Listener listener : listeners) {
            listener.shellChanged(updated);
        }
    }
}
