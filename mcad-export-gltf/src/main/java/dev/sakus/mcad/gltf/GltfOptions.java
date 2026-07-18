/*
 * SPDX-License-Identifier: MPL-2.0
 */
package dev.sakus.mcad.gltf;

import static dev.sakus.mcad.gltf.GltfDiagnostics.boundedMessage;
import static dev.sakus.mcad.gltf.GltfDiagnostics.diagnostic;
import static dev.sakus.mcad.gltf.GltfDiagnostics.id;

import dev.sakus.mcad.api.CanonicalIdentifier;
import dev.sakus.mcad.api.Diagnostic;
import dev.sakus.mcad.api.DiagnosticSeverity;
import dev.sakus.mcad.api.ExportOptions;
import dev.sakus.mcad.api.MetadataValue;
import dev.sakus.mcad.api.Quaterniond;
import dev.sakus.mcad.api.Transform;
import dev.sakus.mcad.api.Vec3d;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

record GltfOptions(Transform transform, LossPolicy lossPolicy) {
    private static final CanonicalIdentifier TRANSLATION = id("gltf/translation");
    private static final CanonicalIdentifier ROTATION = id("gltf/rotation");
    private static final CanonicalIdentifier SCALE = id("gltf/scale");
    private static final CanonicalIdentifier LOSS_POLICY = id("gltf/loss-policy");

    static ParseResult parse(ExportOptions options) {
        List<Diagnostic> diagnostics = new ArrayList<>();
        Vec3d translation = Transform.IDENTITY.translation();
        Quaterniond rotation = Transform.IDENTITY.rotation();
        Vec3d scale = Transform.IDENTITY.scale();
        LossPolicy lossPolicy = LossPolicy.WARNING;

        for (Map.Entry<CanonicalIdentifier, MetadataValue> entry : options.values().entrySet()) {
            CanonicalIdentifier key = entry.getKey();
            try {
                if (key.equals(TRANSLATION)) {
                    double[] value = vector(entry.getValue(), 3, "translation");
                    translation = new Vec3d(value[0], value[1], value[2]);
                } else if (key.equals(ROTATION)) {
                    double[] value = vector(entry.getValue(), 4, "rotation");
                    rotation = new Quaterniond(value[0], value[1], value[2], value[3]);
                } else if (key.equals(SCALE)) {
                    double[] value = vector(entry.getValue(), 3, "scale");
                    scale = new Vec3d(value[0], value[1], value[2]);
                    if (scale.x() == 0.0 || scale.y() == 0.0 || scale.z() == 0.0) {
                        throw new IllegalArgumentException("scale components must be non-zero");
                    }
                } else if (key.equals(LOSS_POLICY)) {
                    if (!(entry.getValue() instanceof MetadataValue.StringValue stringValue)) {
                        throw new IllegalArgumentException("loss-policy must be a string");
                    }
                    lossPolicy = LossPolicy.parse(stringValue.value());
                } else if (key.namespace().equals("mcad") && key.path().startsWith("gltf/")) {
                    diagnostics.add(diagnostic(DiagnosticSeverity.ERROR, "gltf/unknown-option",
                            "Unknown glTF export option: " + key));
                }
            } catch (IllegalArgumentException exception) {
                diagnostics.add(diagnostic(DiagnosticSeverity.ERROR, "gltf/invalid-options",
                        "Invalid option '" + key + "': " + boundedMessage(exception)));
            }
        }

        Transform transform;
        try {
            transform = new Transform(translation, rotation, scale);
        } catch (IllegalArgumentException exception) {
            diagnostics.add(diagnostic(DiagnosticSeverity.ERROR, "gltf/invalid-options",
                    "Invalid export transform: " + boundedMessage(exception)));
            transform = Transform.IDENTITY;
        }
        return new ParseResult(new GltfOptions(transform, lossPolicy), diagnostics);
    }

    private static double[] vector(MetadataValue value, int count, String name) {
        if (!(value instanceof MetadataValue.ListValue listValue) || listValue.values().size() != count) {
            throw new IllegalArgumentException(name + " must be a list of " + count + " numbers");
        }
        double[] result = new double[count];
        for (int index = 0; index < count; index++) {
            MetadataValue component = listValue.values().get(index);
            if (component instanceof MetadataValue.DoubleValue doubleValue) {
                result[index] = doubleValue.value();
            } else if (component instanceof MetadataValue.LongValue longValue) {
                result[index] = longValue.value();
            } else {
                throw new IllegalArgumentException(name + " component " + index + " must be numeric");
            }
        }
        return result;
    }

    enum LossPolicy {
        WARNING(DiagnosticSeverity.WARNING),
        ERROR(DiagnosticSeverity.ERROR);

        private final DiagnosticSeverity severity;

        LossPolicy(DiagnosticSeverity severity) {
            this.severity = severity;
        }

        DiagnosticSeverity severity() {
            return severity;
        }

        static LossPolicy parse(String value) {
            return switch (value.toLowerCase(Locale.ROOT)) {
                case "warning" -> WARNING;
                case "error" -> ERROR;
                default -> throw new IllegalArgumentException("loss-policy must be 'warning' or 'error'");
            };
        }
    }

    record ParseResult(GltfOptions options, List<Diagnostic> diagnostics) {
        ParseResult {
            diagnostics = List.copyOf(diagnostics);
        }
    }
}
