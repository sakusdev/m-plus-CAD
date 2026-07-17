/*
 * SPDX-License-Identifier: MPL-2.0
 */
package dev.sakus.mcad.api;


import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ExporterContractTest {
    @Test
    void capabilitiesAreImmutableAndVersioned() {
        var capabilities = new ExporterCapabilities(
                new SchemaVersion(1, 0), new SchemaVersion(1, 2),
                Set.of(ExporterFeature.MULTIPLE_MESHES), ExporterLimits.unlimited(), Set.of("obj"));
        assertTrue(capabilities.supportsSceneVersion(new SchemaVersion(1, 1)));
        assertFalse(capabilities.supportsSceneVersion(new SchemaVersion(2, 0)));
        assertThrows(UnsupportedOperationException.class, () -> capabilities.features().clear());
    }

    @Test
    void preflightDerivesExportabilityFromDiagnostics() {
        var warning = new Diagnostic(DiagnosticSeverity.WARNING, CanonicalIdentifier.parse("mcad:warning"),
                "warning", Optional.empty(), Map.of());
        var error = new Diagnostic(DiagnosticSeverity.ERROR, CanonicalIdentifier.parse("mcad:error"),
                "error", Optional.empty(), Map.of());
        assertTrue(new PreflightResult(List.of(warning)).canExport());
        assertFalse(new PreflightResult(List.of(error)).canExport());
    }

    @Test
    void producedFilesRejectTraversalAndFailedExportsPublishNothing() {
        assertThrows(IllegalArgumentException.class, () -> new ProducedFile("../model.obj", 1, Optional.empty(), Optional.empty()));
        var file = new ProducedFile("model.obj", 1, Optional.of("model/obj"), Optional.empty());
        assertThrows(IllegalArgumentException.class, () -> new ExportResult(
                ExportStatus.CANCELLED, List.of(file), List.of()));
    }
}
