/*
 * SPDX-License-Identifier: MPL-2.0
 */
package dev.sakus.mcad.api;


import java.util.List;

public record PreflightResult(List<Diagnostic> diagnostics) {
    public PreflightResult {
        diagnostics = Checks.immutableSortedList(diagnostics, Diagnostic.STABLE_ORDER, "diagnostics");
    }

    public boolean canExport() {
        return diagnostics.stream().noneMatch(diagnostic -> diagnostic.severity() == DiagnosticSeverity.ERROR);
    }
}
