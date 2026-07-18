/*
 * SPDX-License-Identifier: MPL-2.0
 */
package dev.sakus.mcad.materials;

import dev.sakus.mcad.api.CanonicalIdentifier;
import dev.sakus.mcad.api.SchemaVersion;
import java.util.Collections;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Objects;
import java.util.TreeMap;

/** Versioned, immutable and deterministically ordered user mapping document. */
public record MaterialMappingDocument(
        SchemaVersion schemaVersion,
        NavigableMap<CanonicalIdentifier, MaterialMapping> mappings) {

    public static final SchemaVersion CURRENT_SCHEMA_VERSION = new SchemaVersion(1, 0);
    public static final int MAX_MAPPINGS = 100_000;

    public MaterialMappingDocument(SchemaVersion schemaVersion, Map<CanonicalIdentifier, MaterialMapping> mappings) {
        this(schemaVersion, sortedMappings(mappings));
    }

    public MaterialMappingDocument {
        Objects.requireNonNull(schemaVersion, "schemaVersion");
        if (!CURRENT_SCHEMA_VERSION.equals(schemaVersion)) {
            throw new IllegalArgumentException("unsupported material mapping schema version: " + schemaVersion);
        }
        mappings = sortedMappings(mappings);
    }

    public static MaterialMappingDocument empty() {
        return new MaterialMappingDocument(CURRENT_SCHEMA_VERSION, Map.of());
    }

    private static NavigableMap<CanonicalIdentifier, MaterialMapping> sortedMappings(
            Map<CanonicalIdentifier, MaterialMapping> source) {
        Objects.requireNonNull(source, "mappings");
        if (source.size() > MAX_MAPPINGS) {
            throw new IllegalArgumentException("too many material mappings: " + source.size());
        }
        var copy = new TreeMap<CanonicalIdentifier, MaterialMapping>();
        for (var entry : source.entrySet()) {
            copy.put(
                    Objects.requireNonNull(entry.getKey(), "mapping blockId"),
                    Objects.requireNonNull(entry.getValue(), "mapping material"));
        }
        return Collections.unmodifiableNavigableMap(copy);
    }
}
