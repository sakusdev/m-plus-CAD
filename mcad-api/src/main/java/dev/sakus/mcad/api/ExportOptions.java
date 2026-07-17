/*
 * SPDX-License-Identifier: MPL-2.0
 */
package dev.sakus.mcad.api;


import java.util.Map;
import java.util.NavigableMap;

public record ExportOptions(NavigableMap<CanonicalIdentifier, MetadataValue> values) {
    public static final ExportOptions EMPTY = new ExportOptions(Map.of());

    public ExportOptions(Map<CanonicalIdentifier, MetadataValue> values) {
        this(Checks.immutableSortedMap(values, CanonicalIdentifier::compareTo, "values"));
    }

    public ExportOptions {
        values = Checks.immutableSortedMap(values, CanonicalIdentifier::compareTo, "values");
    }
}
