/*
 * SPDX-License-Identifier: MPL-2.0
 */
package dev.sakus.mcad.api;


import java.util.Comparator;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Optional;

public record Diagnostic(
        DiagnosticSeverity severity,
        CanonicalIdentifier code,
        String message,
        Optional<SourceReference> source,
        NavigableMap<CanonicalIdentifier, MetadataValue> details) {

    public static final Comparator<Diagnostic> STABLE_ORDER = Comparator
            .comparing(Diagnostic::severity)
            .thenComparing(Diagnostic::code)
            .thenComparing(Diagnostic::message)
            .thenComparing(diagnostic -> diagnostic.source().map(SourceReference::stableSortKey).orElse(""));

    public Diagnostic(
            DiagnosticSeverity severity,
            CanonicalIdentifier code,
            String message,
            Optional<SourceReference> source,
            Map<CanonicalIdentifier, MetadataValue> details) {
        this(severity, code, message, source,
                Checks.immutableSortedMap(details, CanonicalIdentifier::compareTo, "details"));
    }

    public Diagnostic {
        Checks.notNull(severity, "severity");
        Checks.notNull(code, "code");
        Checks.nonBlank(message, "message");
        source = Checks.notNull(source, "source");
        details = Checks.immutableSortedMap(details, CanonicalIdentifier::compareTo, "details");
    }
}
