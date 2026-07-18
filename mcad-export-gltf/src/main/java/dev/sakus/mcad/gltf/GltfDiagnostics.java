/*
 * SPDX-License-Identifier: MPL-2.0
 */
package dev.sakus.mcad.gltf;

import dev.sakus.mcad.api.CanonicalIdentifier;
import dev.sakus.mcad.api.Diagnostic;
import dev.sakus.mcad.api.DiagnosticSeverity;

import java.util.Map;
import java.util.Optional;

final class GltfDiagnostics {
    private GltfDiagnostics() {
    }

    static CanonicalIdentifier id(String path) {
        return new CanonicalIdentifier("mcad", path);
    }

    static Diagnostic diagnostic(DiagnosticSeverity severity, String code, String message) {
        return new Diagnostic(severity, id(code), message, Optional.empty(), Map.of());
    }

    static String boundedMessage(Throwable throwable) {
        String message = throwable.getMessage();
        if (message == null || message.isBlank()) {
            message = throwable.getClass().getSimpleName();
        }
        return message.length() <= 512 ? message : message.substring(0, 512);
    }
}
