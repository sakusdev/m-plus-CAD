/*
 * SPDX-License-Identifier: MPL-2.0
 */
package dev.sakus.mcad.api;


import java.util.Collection;
import java.util.Locale;
import java.util.NavigableSet;
import java.util.Set;
import java.util.TreeSet;

public record ExporterCapabilities(
        SchemaVersion minimumSceneVersion,
        SchemaVersion maximumSceneVersion,
        NavigableSet<ExporterFeature> features,
        ExporterLimits limits,
        NavigableSet<String> fileExtensions) {

    public ExporterCapabilities(
            SchemaVersion minimumSceneVersion,
            SchemaVersion maximumSceneVersion,
            Collection<ExporterFeature> features,
            ExporterLimits limits,
            Collection<String> fileExtensions) {
        this(minimumSceneVersion, maximumSceneVersion,
                Checks.immutableSortedSet(features, ExporterFeature::compareTo, "features"),
                limits,
                copyExtensions(fileExtensions));
    }

    public ExporterCapabilities {
        Checks.notNull(minimumSceneVersion, "minimumSceneVersion");
        Checks.notNull(maximumSceneVersion, "maximumSceneVersion");
        if (minimumSceneVersion.compareTo(maximumSceneVersion) > 0) {
            throw new IllegalArgumentException("minimumSceneVersion must not exceed maximumSceneVersion");
        }
        features = Checks.immutableSortedSet(features, ExporterFeature::compareTo, "features");
        Checks.notNull(limits, "limits");
        fileExtensions = copyExtensions(fileExtensions);
        if (fileExtensions.isEmpty()) {
            throw new IllegalArgumentException("fileExtensions must not be empty");
        }
    }

    public boolean supportsSceneVersion(SchemaVersion version) {
        Checks.notNull(version, "version");
        return minimumSceneVersion.compareTo(version) <= 0 && maximumSceneVersion.compareTo(version) >= 0;
    }

    private static NavigableSet<String> copyExtensions(Collection<String> values) {
        Checks.notNull(values, "fileExtensions");
        Set<String> copy = new TreeSet<>();
        for (String value : values) {
            String normalized = Checks.nonBlank(value, "file extension").toLowerCase(Locale.ROOT);
            if (normalized.startsWith(".") || !normalized.matches("[a-z0-9]+")) {
                throw new IllegalArgumentException("file extension must omit the dot and be alphanumeric: " + value);
            }
            copy.add(normalized);
        }
        if (copy.size() != values.size()) {
            throw new IllegalArgumentException("fileExtensions must not contain duplicates");
        }
        return Checks.immutableSortedSet(copy, String::compareTo, "fileExtensions");
    }
}
