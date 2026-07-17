/*
 * SPDX-License-Identifier: MPL-2.0
 */
package dev.sakus.mcad.markers;

import dev.sakus.mcad.api.BlockEntry;
import dev.sakus.mcad.api.CanonicalIdentifier;
import dev.sakus.mcad.api.Diagnostic;
import dev.sakus.mcad.api.MetadataValue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Objects;
import java.util.TreeMap;

/** Extension point for neutral custom marker actions. */
public interface CustomMarkerAction {
    CanonicalIdentifier actionId();

    Outcome apply(Context context);

    /** Contains only detached neutral values; no live world, registry, exporter, script engine, or path API. */
    record Context(
            String snapshotId,
            BlockEntry source,
            MarkerRule rule,
            NavigableMap<CanonicalIdentifier, MetadataValue> parameters) {
        public Context(
                String snapshotId,
                BlockEntry source,
                MarkerRule rule,
                Map<CanonicalIdentifier, MetadataValue> parameters) {
            this(snapshotId, source, rule, immutableMetadata(parameters));
        }

        public Context {
            Objects.requireNonNull(snapshotId, "snapshotId");
            if (snapshotId.isBlank() || snapshotId.indexOf('\0') >= 0) {
                throw new IllegalArgumentException("snapshotId must be non-blank and contain no NUL");
            }
            Objects.requireNonNull(source, "source");
            Objects.requireNonNull(rule, "rule");
            parameters = immutableMetadata(parameters);
        }
    }

    record Outcome(List<MarkerDirective> directives, List<Diagnostic> diagnostics) {
        public static final Outcome EMPTY = new Outcome(List.of(), List.of());

        public Outcome {
            Objects.requireNonNull(directives, "directives");
            Objects.requireNonNull(diagnostics, "diagnostics");
            var directiveCopy = new ArrayList<MarkerDirective>(directives.size());
            for (MarkerDirective directive : directives) {
                directiveCopy.add(Objects.requireNonNull(directive, "directive"));
            }
            directives = List.copyOf(directiveCopy);
            var diagnosticCopy = new ArrayList<Diagnostic>(diagnostics.size());
            for (Diagnostic diagnostic : diagnostics) {
                diagnosticCopy.add(Objects.requireNonNull(diagnostic, "diagnostic"));
            }
            diagnostics = List.copyOf(diagnosticCopy);
        }
    }

    private static NavigableMap<CanonicalIdentifier, MetadataValue> immutableMetadata(
            Map<CanonicalIdentifier, MetadataValue> values) {
        Objects.requireNonNull(values, "parameters");
        var copy = new TreeMap<CanonicalIdentifier, MetadataValue>();
        for (var entry : values.entrySet()) {
            copy.put(
                    Objects.requireNonNull(entry.getKey(), "parameter key"),
                    Objects.requireNonNull(entry.getValue(), "parameter value"));
        }
        return Collections.unmodifiableNavigableMap(copy);
    }
}
