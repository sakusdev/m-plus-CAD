/*
 * SPDX-License-Identifier: MPL-2.0
 */
package dev.sakus.mcad.materials;

import dev.sakus.mcad.api.Diagnostic;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Deterministically ordered outcome of an explicit user-asset validation pass. */
public record AssetValidationResult(
        List<ValidatedUserAsset> validAssets,
        List<Diagnostic> diagnostics) {

    public AssetValidationResult {
        Objects.requireNonNull(validAssets, "validAssets");
        Objects.requireNonNull(diagnostics, "diagnostics");
        var sortedAssets = new ArrayList<ValidatedUserAsset>(validAssets);
        sortedAssets.sort(ValidatedUserAsset::compareTo);
        validAssets = List.copyOf(sortedAssets);
        var sortedDiagnostics = new ArrayList<Diagnostic>(diagnostics);
        sortedDiagnostics.sort(Diagnostic.STABLE_ORDER);
        diagnostics = List.copyOf(sortedDiagnostics);
    }
}
