/*
 * SPDX-License-Identifier: MPL-2.0
 */
package dev.sakus.mcad.minecraft.runtime;

import dev.sakus.mcad.api.CanonicalIdentifier;
import dev.sakus.mcad.api.ModelExporter;
import dev.sakus.mcad.export.obj.ObjExporter;
import dev.sakus.mcad.gltf.GltfExporter;

import java.util.Collection;
import java.util.Collections;
import java.util.NavigableMap;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;

/** Deterministic registry of the exporters shipped by the client module. */
public final class ExporterRegistry {
    private final NavigableMap<CanonicalIdentifier, ModelExporter> exporters;

    public ExporterRegistry(Collection<? extends ModelExporter> values) {
        Objects.requireNonNull(values, "values");
        var registered = new TreeMap<CanonicalIdentifier, ModelExporter>();
        for (ModelExporter exporter : values) {
            ModelExporter checked = Objects.requireNonNull(exporter, "exporter");
            if (registered.putIfAbsent(checked.formatId(), checked) != null) {
                throw new IllegalArgumentException("duplicate exporter ID: " + checked.formatId());
            }
        }
        exporters = Collections.unmodifiableNavigableMap(registered);
    }

    public static ExporterRegistry builtIns() {
        return new ExporterRegistry(java.util.List.of(new GltfExporter(), new ObjExporter()));
    }

    public Optional<ModelExporter> find(CanonicalIdentifier id) {
        return Optional.ofNullable(exporters.get(Objects.requireNonNull(id, "id")));
    }

    public ModelExporter require(CanonicalIdentifier id) {
        return find(id).orElseThrow(() -> new IllegalArgumentException("unknown exporter ID: " + id));
    }

    public NavigableMap<CanonicalIdentifier, ModelExporter> exporters() {
        return exporters;
    }
}
