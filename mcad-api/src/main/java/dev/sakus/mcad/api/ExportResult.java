/*
 * SPDX-License-Identifier: MPL-2.0
 */
package dev.sakus.mcad.api;


import java.util.List;

public record ExportResult(ExportStatus status, List<ProducedFile> producedFiles, List<Diagnostic> diagnostics) {
    public ExportResult {
        Checks.notNull(status, "status");
        producedFiles = Checks.immutableSortedList(producedFiles, ProducedFile::compareTo, "producedFiles");
        Checks.requireNoDuplicates(producedFiles, "producedFiles");
        diagnostics = Checks.immutableSortedList(diagnostics, Diagnostic.STABLE_ORDER, "diagnostics");
        boolean hasError = diagnostics.stream().anyMatch(diagnostic -> diagnostic.severity() == DiagnosticSeverity.ERROR);
        if (status == ExportStatus.SUCCESS && hasError) {
            throw new IllegalArgumentException("successful export cannot contain error diagnostics");
        }
        if (status != ExportStatus.SUCCESS && !producedFiles.isEmpty()) {
            throw new IllegalArgumentException("failed or cancelled export must not publish produced files");
        }
    }
}
