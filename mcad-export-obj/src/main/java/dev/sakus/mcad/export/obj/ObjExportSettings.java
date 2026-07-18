/*
 * SPDX-License-Identifier: MPL-2.0
 */
package dev.sakus.mcad.export.obj;

import dev.sakus.mcad.api.CanonicalIdentifier;
import dev.sakus.mcad.api.Diagnostic;
import dev.sakus.mcad.api.DiagnosticSeverity;
import dev.sakus.mcad.api.ExportOptions;
import dev.sakus.mcad.api.MetadataValue;
import dev.sakus.mcad.api.Vec3d;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

record ObjExportSettings(
        Vec3d originOffset,
        Vec3d rotationDegrees,
        double unitScale,
        TargetAxis targetAxis,
        LossPolicy lossPolicy) {

    static final CanonicalIdentifier ORIGIN_OFFSET = CanonicalIdentifier.parse("mcad:transform/origin_offset");
    static final CanonicalIdentifier ROTATION_DEGREES = CanonicalIdentifier.parse("mcad:transform/rotation_degrees");
    static final CanonicalIdentifier UNIT_SCALE = CanonicalIdentifier.parse("mcad:transform/unit_scale");
    static final CanonicalIdentifier TARGET_AXIS = CanonicalIdentifier.parse("mcad:transform/target_axis");
    static final CanonicalIdentifier LOSS_POLICY = CanonicalIdentifier.parse("mcad:loss_policy");

    private static final CanonicalIdentifier INVALID_OPTION = CanonicalIdentifier.parse("mcad:obj/invalid_option");
    private static final CanonicalIdentifier UNKNOWN_OPTION = CanonicalIdentifier.parse("mcad:obj/unknown_option");
    private static final Set<CanonicalIdentifier> KNOWN_OPTIONS = Set.of(
            ORIGIN_OFFSET, ROTATION_DEGREES, UNIT_SCALE, TARGET_AXIS, LOSS_POLICY);

    static ParseResult parse(ExportOptions options) {
        List<Diagnostic> diagnostics = new ArrayList<>();
        Vec3d originOffset = vector(options, ORIGIN_OFFSET, Vec3d.ZERO, diagnostics);
        Vec3d rotationDegrees = vector(options, ROTATION_DEGREES, Vec3d.ZERO, diagnostics);
        double unitScale = number(options, UNIT_SCALE, 1.0, diagnostics);
        if (!(unitScale > 0.0) || !Double.isFinite(unitScale)) {
            diagnostics.add(error(UNIT_SCALE + " must be a finite positive number"));
        }
        TargetAxis targetAxis = enumValue(
                options, TARGET_AXIS, TargetAxis.INTERNAL_RIGHT_HANDED_Y_UP, TargetAxis.class, diagnostics);
        LossPolicy lossPolicy = enumValue(
                options, LOSS_POLICY, LossPolicy.WARN_AND_CONTINUE, LossPolicy.class, diagnostics);

        for (CanonicalIdentifier key : options.values().keySet()) {
            if (!KNOWN_OPTIONS.contains(key)) {
                diagnostics.add(new Diagnostic(
                        DiagnosticSeverity.WARNING,
                        UNKNOWN_OPTION,
                        "OBJ exporter ignored unknown option: " + key,
                        Optional.empty(),
                        Map.of()));
            }
        }

        boolean invalid = diagnostics.stream().anyMatch(diagnostic -> diagnostic.severity() == DiagnosticSeverity.ERROR);
        Optional<ObjExportSettings> settings = invalid
                ? Optional.empty()
                : Optional.of(new ObjExportSettings(originOffset, rotationDegrees, unitScale, targetAxis, lossPolicy));
        return new ParseResult(settings, diagnostics);
    }

    DiagnosticSeverity unsupportedSeverity() {
        return lossPolicy == LossPolicy.FAIL ? DiagnosticSeverity.ERROR : DiagnosticSeverity.WARNING;
    }

    private static Vec3d vector(
            ExportOptions options,
            CanonicalIdentifier key,
            Vec3d defaultValue,
            List<Diagnostic> diagnostics) {
        MetadataValue value = options.values().get(key);
        if (value == null) {
            return defaultValue;
        }
        if (!(value instanceof MetadataValue.ListValue list) || list.values().size() != 3) {
            diagnostics.add(error(key + " must be a three-number list"));
            return defaultValue;
        }
        double[] components = new double[3];
        for (int index = 0; index < 3; index++) {
            Optional<Double> component = asNumber(list.values().get(index));
            if (component.isEmpty()) {
                diagnostics.add(error(key + " must contain only finite numbers"));
                return defaultValue;
            }
            components[index] = component.orElseThrow();
        }
        return new Vec3d(components[0], components[1], components[2]);
    }

    private static double number(
            ExportOptions options,
            CanonicalIdentifier key,
            double defaultValue,
            List<Diagnostic> diagnostics) {
        MetadataValue value = options.values().get(key);
        if (value == null) {
            return defaultValue;
        }
        Optional<Double> parsed = asNumber(value);
        if (parsed.isEmpty()) {
            diagnostics.add(error(key + " must be a finite number"));
            return defaultValue;
        }
        return parsed.orElseThrow();
    }

    private static Optional<Double> asNumber(MetadataValue value) {
        if (value instanceof MetadataValue.DoubleValue doubleValue) {
            return Optional.of(doubleValue.value());
        }
        if (value instanceof MetadataValue.LongValue longValue) {
            return Optional.of((double) longValue.value());
        }
        return Optional.empty();
    }

    private static <E extends Enum<E>> E enumValue(
            ExportOptions options,
            CanonicalIdentifier key,
            E defaultValue,
            Class<E> enumClass,
            List<Diagnostic> diagnostics) {
        MetadataValue value = options.values().get(key);
        if (value == null) {
            return defaultValue;
        }
        if (!(value instanceof MetadataValue.StringValue stringValue)) {
            diagnostics.add(error(key + " must be a string"));
            return defaultValue;
        }
        String normalized = stringValue.value().trim().toUpperCase(Locale.ROOT).replace('-', '_');
        try {
            return Enum.valueOf(enumClass, normalized);
        } catch (IllegalArgumentException exception) {
            diagnostics.add(error(key + " has unsupported value: " + bounded(stringValue.value())));
            return defaultValue;
        }
    }

    private static Diagnostic error(String message) {
        return new Diagnostic(
                DiagnosticSeverity.ERROR,
                INVALID_OPTION,
                bounded(message),
                Optional.empty(),
                Map.of());
    }

    private static String bounded(String value) {
        return value.length() <= 512 ? value : value.substring(0, 509) + "...";
    }

    enum TargetAxis {
        INTERNAL_RIGHT_HANDED_Y_UP,
        RIGHT_HANDED_Z_UP,
        LEFT_HANDED_Y_UP
    }

    enum LossPolicy {
        FAIL,
        WARN_AND_CONTINUE
    }

    record ParseResult(Optional<ObjExportSettings> settings, List<Diagnostic> diagnostics) {
        ParseResult {
            settings = Objects.requireNonNull(settings, "settings");
            diagnostics = List.copyOf(diagnostics);
        }
    }
}
