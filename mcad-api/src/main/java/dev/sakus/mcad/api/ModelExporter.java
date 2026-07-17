/*
 * SPDX-License-Identifier: MPL-2.0
 */
package dev.sakus.mcad.api;


import java.io.IOException;
import java.nio.file.Path;
import java.util.NavigableSet;

public interface ModelExporter {
    CanonicalIdentifier formatId();

    String displayName();

    NavigableSet<String> fileExtensions();

    ExporterCapabilities capabilities();

    PreflightResult preflight(GeneratedScene scene, ExportOptions options);

    ExportResult export(
            GeneratedScene scene,
            Path destination,
            ExportOptions options,
            ProgressReporter progress,
            CancellationToken cancellation) throws IOException;
}
