/*
 * SPDX-License-Identifier: MPL-2.0
 */
package dev.sakus.mcad.markers;

import dev.sakus.mcad.api.BlockEntry;
import dev.sakus.mcad.api.BlockPosition;
import dev.sakus.mcad.api.CameraProjection;
import dev.sakus.mcad.api.CancellationToken;
import dev.sakus.mcad.api.CanonicalIdentifier;
import dev.sakus.mcad.api.CollisionKind;
import dev.sakus.mcad.api.Color3d;
import dev.sakus.mcad.api.Diagnostic;
import dev.sakus.mcad.api.DiagnosticSeverity;
import dev.sakus.mcad.api.MetadataValue;
import dev.sakus.mcad.api.ProgressReporter;
import dev.sakus.mcad.api.ProgressUpdate;
import dev.sakus.mcad.api.Quaterniond;
import dev.sakus.mcad.api.SourceReference;
import dev.sakus.mcad.api.StructureSnapshot;
import dev.sakus.mcad.api.Transform;
import dev.sakus.mcad.api.Vec3d;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalLong;
import java.util.TreeMap;
import java.util.concurrent.CancellationException;

/** Deterministic interpreter for versioned, user-configurable marker rules. */
public final class ConfigurableMarkerInterpreter {
    private static final CanonicalIdentifier UNSUPPORTED_SCHEMA = code("unsupported_marker_rule_schema");
    private static final CanonicalIdentifier RULE_CONFLICT = code("marker_rule_conflict");
    private static final CanonicalIdentifier INVALID_PARAMETERS = code("invalid_marker_parameters");
    private static final CanonicalIdentifier UNKNOWN_CUSTOM_ACTION = code("unknown_custom_marker_action");
    private static final CanonicalIdentifier CUSTOM_ACTION_FAILED = code("custom_marker_action_failed");
    private static final CanonicalIdentifier DIRECTIVE_ID_CONFLICT = code("marker_directive_id_conflict");
    private static final CanonicalIdentifier CHAIN_ORDER_CONFLICT = code("marker_chain_order_conflict");
    private static final CanonicalIdentifier INVALID_CHAIN = code("invalid_marker_chain");
    private static final CanonicalIdentifier MULTIPLE_ORIGINS = code("multiple_origin_markers");
    private static final CanonicalIdentifier PROPERTY_CONFLICT = code("custom_property_conflict");
    private static final CanonicalIdentifier DETAILS_RULE_IDS = code("rule_ids");
    private static final CanonicalIdentifier DETAILS_SCHEMA = code("schema_version");
    private static final CanonicalIdentifier DETAILS_STABLE_ID = code("stable_id");
    private static final CanonicalIdentifier DETAILS_CHAIN_ID = code("chain_id");
    private static final CanonicalIdentifier DETAILS_PROPERTY_KEY = code("property_key");
    private static final int PROGRESS_INTERVAL = 256;

    private final NavigableMap<CanonicalIdentifier, CustomMarkerAction> customActions;

    public ConfigurableMarkerInterpreter() {
        this(List.of());
    }

    public ConfigurableMarkerInterpreter(Collection<? extends CustomMarkerAction> customActions) {
        Objects.requireNonNull(customActions, "customActions");
        var registry = new TreeMap<CanonicalIdentifier, CustomMarkerAction>();
        for (CustomMarkerAction action : customActions) {
            CustomMarkerAction checked = Objects.requireNonNull(action, "customAction");
            CanonicalIdentifier id = Objects.requireNonNull(checked.actionId(), "customAction.actionId");
            if (registry.putIfAbsent(id, checked) != null) {
                throw new IllegalArgumentException("duplicate custom marker action ID: " + id);
            }
        }
        this.customActions = Collections.unmodifiableNavigableMap(registry);
    }

    public MarkerInterpretationResult interpret(StructureSnapshot snapshot, MarkerRuleSet ruleSet) {
        return interpret(snapshot, ruleSet, CancellationToken.NONE, ProgressReporter.NONE);
    }

    public MarkerInterpretationResult interpret(
            StructureSnapshot snapshot,
            MarkerRuleSet ruleSet,
            CancellationToken cancellationToken,
            ProgressReporter progressReporter) {
        Objects.requireNonNull(snapshot, "snapshot");
        Objects.requireNonNull(ruleSet, "ruleSet");
        Objects.requireNonNull(cancellationToken, "cancellationToken");
        Objects.requireNonNull(progressReporter, "progressReporter");

        if (ruleSet.schemaVersion().major() != MarkerRuleSet.CURRENT_SCHEMA_VERSION.major()) {
            Diagnostic diagnostic = diagnostic(
                    DiagnosticSeverity.ERROR,
                    UNSUPPORTED_SCHEMA,
                    "Unsupported marker-rule schema: " + ruleSet.schemaVersion(),
                    Optional.empty(),
                    Map.of(DETAILS_SCHEMA, new MetadataValue.StringValue(ruleSet.schemaVersion().toString())));
            return new MarkerInterpretationResult(
                    snapshot.blocks(), List.of(), List.of(), List.of(diagnostic), List.of());
        }

        var retained = new ArrayList<BlockEntry>();
        var consumed = new ArrayList<BlockPosition>();
        var directivesById = new LinkedHashMap<String, MarkerDirective>();
        var diagnostics = new ArrayList<Diagnostic>();
        var applications = new ArrayList<MarkerInterpretationResult.RuleApplication>();
        var curveCandidates = new TreeMap<CanonicalIdentifier, List<CurvePointCandidate>>();
        var originCandidates = new ArrayList<OriginCandidate>();
        var customPropertyOwners = new HashMap<CanonicalIdentifier, String>();

        long total = snapshot.blocks().size();
        progressReporter.report(new ProgressUpdate(
                "marker/interpretation", 0, OptionalLong.of(total), "Interpreting marker rules"));

        long completed = 0;
        for (BlockEntry block : snapshot.blocks()) {
            cancellationToken.throwIfCancellationRequested();
            List<MarkerRule> matches = matchingRules(ruleSet.rules(), block);
            if (matches.size() > 1 && matches.get(0).priority() == matches.get(1).priority()) {
                int highestPriority = matches.get(0).priority();
                List<MarkerRule> ambiguous = matches.stream()
                        .filter(rule -> rule.priority() == highestPriority)
                        .toList();
                diagnostics.add(conflictDiagnostic(snapshot, block, ambiguous));
            }

            boolean sourceConsumed = false;
            for (MarkerRule rule : matches) {
                SourceReference source = sourceReference(snapshot, block, rule);
                try {
                    ActionApplication application = applyAction(snapshot, block, rule, source);
                    diagnostics.addAll(application.diagnostics());
                    if (!application.successful()) {
                        continue;
                    }
                    var acceptedDirectiveIds = new ArrayList<String>();
                    for (MarkerDirective directive : application.directives()) {
                        if (addDirective(
                                directive,
                                source,
                                directivesById,
                                customPropertyOwners,
                                diagnostics)) {
                            acceptedDirectiveIds.add(directive.stableId());
                        }
                    }
                    application.curvePoint().ifPresent(candidate -> curveCandidates
                            .computeIfAbsent(candidate.chainId(), ignored -> new ArrayList<>())
                            .add(candidate));
                    application.origin().ifPresent(originCandidates::add);
                    applications.add(new MarkerInterpretationResult.RuleApplication(
                            block.relativePosition(),
                            rule.ruleId(),
                            rule.sourcePolicy().consumesSource(),
                            acceptedDirectiveIds));
                    sourceConsumed |= rule.sourcePolicy().consumesSource();
                    if (!rule.sourcePolicy().continuesEvaluation()) {
                        break;
                    }
                } catch (CancellationException exception) {
                    throw exception;
                } catch (IllegalArgumentException exception) {
                    diagnostics.add(diagnostic(
                            DiagnosticSeverity.ERROR,
                            INVALID_PARAMETERS,
                            "Invalid parameters for marker rule " + rule.ruleId() + ": "
                                    + boundedMessage(exception.getMessage()),
                            Optional.of(source),
                            Map.of()));
                } catch (RuntimeException exception) {
                    diagnostics.add(diagnostic(
                            DiagnosticSeverity.ERROR,
                            CUSTOM_ACTION_FAILED,
                            "Marker action failed safely for rule " + rule.ruleId() + ": "
                                    + boundedMessage(exception.getMessage()),
                            Optional.of(source),
                            Map.of()));
                }
            }

            if (sourceConsumed) {
                consumed.add(block.relativePosition());
            } else {
                retained.add(block);
            }

            completed++;
            if (completed == total || completed % PROGRESS_INTERVAL == 0) {
                progressReporter.report(new ProgressUpdate(
                        "marker/interpretation",
                        completed,
                        OptionalLong.of(total),
                        "Interpreted " + completed + " of " + total + " source blocks"));
            }
        }

        assembleCurves(curveCandidates, directivesById, diagnostics);
        resolveOrigin(originCandidates, directivesById, diagnostics);

        return new MarkerInterpretationResult(
                retained,
                consumed,
                List.copyOf(directivesById.values()),
                diagnostics,
                applications);
    }

    private ActionApplication applyAction(
            StructureSnapshot snapshot,
            BlockEntry block,
            MarkerRule rule,
            SourceReference source) {
        NavigableMap<CanonicalIdentifier, MetadataValue> parameters = rule.action().parameters();
        String stableId = stableDirectiveId(rule, block);
        Vec3d position = markerPosition(block, parameters);
        Transform transform = markerTransform(block, parameters);
        String defaultName = rule.action().type().name().toLowerCase(Locale.ROOT)
                + " " + block.relativePosition().x() + "," + block.relativePosition().y() + ","
                + block.relativePosition().z();
        String name = sanitizedName(optionalString(parameters, MarkerRule.Parameters.NAME).orElse(defaultName), defaultName);
        List<SourceReference> sources = List.of(source);

        return switch (rule.action().type()) {
            case ORIGIN -> new ActionApplication(
                    true,
                    List.of(),
                    Optional.empty(),
                    Optional.of(new OriginCandidate(rule, block.relativePosition(),
                            new MarkerDirective.Origin(stableId, position, sources))),
                    List.of());
            case POINT_LIGHT -> {
                Color3d colour = new Color3d(
                        number(parameters, MarkerRule.Parameters.COLOUR_RED, 1.0),
                        number(parameters, MarkerRule.Parameters.COLOUR_GREEN, 1.0),
                        number(parameters, MarkerRule.Parameters.COLOUR_BLUE, 1.0));
                double intensity = number(parameters, MarkerRule.Parameters.INTENSITY, 1000.0);
                OptionalDouble range = optionalNumber(parameters, MarkerRule.Parameters.RANGE);
                yield ActionApplication.of(new MarkerDirective.PointLight(
                        stableId, name, transform, colour, intensity, range, sources));
            }
            case CAMERA -> {
                CameraProjection projection = parseProjection(
                        optionalString(parameters, MarkerRule.Parameters.PROJECTION).orElse("perspective"));
                double nearPlane = number(parameters, MarkerRule.Parameters.NEAR_PLANE, 0.1);
                OptionalDouble farPlane = optionalNumber(parameters, MarkerRule.Parameters.FAR_PLANE);
                OptionalDouble fov = projection == CameraProjection.PERSPECTIVE
                        ? OptionalDouble.of(number(
                                parameters,
                                MarkerRule.Parameters.FIELD_OF_VIEW_RADIANS,
                                Math.toRadians(60.0)))
                        : OptionalDouble.empty();
                OptionalDouble height = projection == CameraProjection.ORTHOGRAPHIC
                        ? OptionalDouble.of(number(parameters, MarkerRule.Parameters.ORTHOGRAPHIC_HEIGHT, 10.0))
                        : OptionalDouble.empty();
                yield ActionApplication.of(new MarkerDirective.Camera(
                        stableId, name, transform, projection, nearPlane, farPlane, fov, height, sources));
            }
            case CURVE_POINT -> {
                CanonicalIdentifier chainId = CanonicalIdentifier.parse(
                        requiredString(parameters, MarkerRule.Parameters.CHAIN_ID));
                long order = requiredLong(parameters, MarkerRule.Parameters.CHAIN_ORDER);
                boolean closed = booleanValue(parameters, MarkerRule.Parameters.CLOSED, false);
                yield new ActionApplication(
                        true,
                        List.of(),
                        Optional.of(new CurvePointCandidate(
                                chainId, name, order, position, closed, source, rule, block.relativePosition())),
                        Optional.empty(),
                        List.of());
            }
            case BONE -> {
                Optional<String> parentId = optionalString(parameters, MarkerRule.Parameters.PARENT_ID)
                        .map(ConfigurableMarkerInterpreter::sanitizedStableId);
                yield ActionApplication.of(new MarkerDirective.Bone(
                        stableId, name, parentId, transform, sources));
            }
            case GROUP -> ActionApplication.of(new MarkerDirective.Group(stableId, name, sources));
            case COLLISION -> {
                CollisionKind kind = parseCollisionKind(
                        optionalString(parameters, MarkerRule.Parameters.COLLISION_KIND).orElse("solid"));
                Optional<String> targetGroup = optionalString(parameters, MarkerRule.Parameters.TARGET_GROUP)
                        .map(ConfigurableMarkerInterpreter::sanitizedStableId);
                yield ActionApplication.of(new MarkerDirective.Collision(
                        stableId, name, kind, targetGroup, sources));
            }
            case CUSTOM_PROPERTY -> {
                CanonicalIdentifier key = CanonicalIdentifier.parse(
                        requiredString(parameters, MarkerRule.Parameters.PROPERTY_KEY));
                MetadataValue value = Objects.requireNonNull(
                        parameters.get(MarkerRule.Parameters.PROPERTY_VALUE),
                        "missing required parameter " + MarkerRule.Parameters.PROPERTY_VALUE);
                yield ActionApplication.of(new MarkerDirective.CustomProperty(
                        stableId, key, value, sources));
            }
            case CUSTOM -> applyCustomAction(snapshot, block, rule);
        };
    }

    private ActionApplication applyCustomAction(
            StructureSnapshot snapshot,
            BlockEntry block,
            MarkerRule rule) {
        CanonicalIdentifier actionId = rule.action().customActionId().orElseThrow();
        CustomMarkerAction action = customActions.get(actionId);
        SourceReference source = sourceReference(snapshot, block, rule);
        if (action == null) {
            Diagnostic diagnostic = diagnostic(
                    DiagnosticSeverity.ERROR,
                    UNKNOWN_CUSTOM_ACTION,
                    "Unknown custom marker action: " + actionId,
                    Optional.of(source),
                    Map.of());
            return new ActionApplication(false, List.of(), Optional.empty(), Optional.empty(), List.of(diagnostic));
        }
        CustomMarkerAction.Outcome outcome = Objects.requireNonNull(
                action.apply(new CustomMarkerAction.Context(
                        snapshot.snapshotId(), block, rule, rule.action().parameters())),
                "custom marker action outcome");
        return new ActionApplication(true, outcome.directives(), Optional.empty(), Optional.empty(), outcome.diagnostics());
    }

    private static List<MarkerRule> matchingRules(List<MarkerRule> rules, BlockEntry block) {
        var matches = new ArrayList<MarkerRule>();
        for (MarkerRule rule : rules) {
            if (rule.enabled() && rule.match().matches(block)) {
                matches.add(rule);
            }
        }
        return List.copyOf(matches);
    }

    private static Diagnostic conflictDiagnostic(
            StructureSnapshot snapshot,
            BlockEntry block,
            List<MarkerRule> rules) {
        MarkerRule first = rules.get(0);
        List<MetadataValue> ids = rules.stream()
                .map(rule -> (MetadataValue) new MetadataValue.StringValue(rule.ruleId().toString()))
                .toList();
        return diagnostic(
                DiagnosticSeverity.WARNING,
                RULE_CONFLICT,
                "Multiple marker rules have the same highest priority at " + block.relativePosition()
                        + "; stable rule ID order is used",
                Optional.of(sourceReference(snapshot, block, first)),
                Map.of(DETAILS_RULE_IDS, new MetadataValue.ListValue(ids)));
    }

    private static boolean addDirective(
            MarkerDirective directive,
            SourceReference source,
            Map<String, MarkerDirective> directivesById,
            Map<CanonicalIdentifier, String> customPropertyOwners,
            List<Diagnostic> diagnostics) {
        MarkerDirective existing = directivesById.get(directive.stableId());
        if (existing != null) {
            diagnostics.add(diagnostic(
                    DiagnosticSeverity.ERROR,
                    DIRECTIVE_ID_CONFLICT,
                    "Duplicate marker directive stable ID: " + directive.stableId(),
                    Optional.of(source),
                    Map.of(DETAILS_STABLE_ID, new MetadataValue.StringValue(directive.stableId()))));
            return false;
        }
        if (directive instanceof MarkerDirective.CustomProperty property) {
            String owner = customPropertyOwners.putIfAbsent(property.key(), property.stableId());
            if (owner != null) {
                diagnostics.add(diagnostic(
                        DiagnosticSeverity.WARNING,
                        PROPERTY_CONFLICT,
                        "Multiple markers set custom property " + property.key()
                                + "; the first deterministic value is retained",
                        Optional.of(source),
                        Map.of(DETAILS_PROPERTY_KEY, new MetadataValue.StringValue(property.key().toString()))));
                return false;
            }
        }
        directivesById.put(directive.stableId(), directive);
        return true;
    }

    private static void assembleCurves(
            NavigableMap<CanonicalIdentifier, List<CurvePointCandidate>> candidatesByChain,
            Map<String, MarkerDirective> directivesById,
            List<Diagnostic> diagnostics) {
        Comparator<CurvePointCandidate> order = Comparator
                .comparingLong(CurvePointCandidate::order)
                .thenComparing(CurvePointCandidate::sourcePosition)
                .thenComparing(candidate -> candidate.rule().ruleId());
        for (var entry : candidatesByChain.entrySet()) {
            CanonicalIdentifier chainId = entry.getKey();
            var candidates = new ArrayList<CurvePointCandidate>(entry.getValue());
            candidates.sort(order);
            if (candidates.size() < 2) {
                CurvePointCandidate candidate = candidates.get(0);
                diagnostics.add(diagnostic(
                        DiagnosticSeverity.ERROR,
                        INVALID_CHAIN,
                        "Curve chain " + chainId + " requires at least two points",
                        Optional.of(candidate.source()),
                        Map.of(DETAILS_CHAIN_ID, new MetadataValue.StringValue(chainId.toString()))));
                continue;
            }
            for (int index = 1; index < candidates.size(); index++) {
                if (candidates.get(index - 1).order() == candidates.get(index).order()) {
                    diagnostics.add(diagnostic(
                            DiagnosticSeverity.WARNING,
                            CHAIN_ORDER_CONFLICT,
                            "Curve chain " + chainId + " contains duplicate order "
                                    + candidates.get(index).order() + "; source position and rule ID break the tie",
                            Optional.of(candidates.get(index).source()),
                            Map.of(DETAILS_CHAIN_ID, new MetadataValue.StringValue(chainId.toString()))));
                }
            }
            CurvePointCandidate first = candidates.get(0);
            boolean closed = first.closed();
            String name = first.name();
            var points = new ArrayList<Vec3d>(candidates.size());
            var sources = new ArrayList<SourceReference>(candidates.size());
            for (CurvePointCandidate candidate : candidates) {
                points.add(candidate.position());
                sources.add(candidate.source());
            }
            String stableId = "marker/curve/" + chainId.namespace() + "/" + chainId.path();
            MarkerDirective.Curve curve = new MarkerDirective.Curve(stableId, name, points, closed, sources);
            if (directivesById.putIfAbsent(stableId, curve) != null) {
                diagnostics.add(diagnostic(
                        DiagnosticSeverity.ERROR,
                        DIRECTIVE_ID_CONFLICT,
                        "Duplicate marker directive stable ID: " + stableId,
                        Optional.of(first.source()),
                        Map.of(DETAILS_STABLE_ID, new MetadataValue.StringValue(stableId))));
            }
        }
    }

    private static void resolveOrigin(
            List<OriginCandidate> candidates,
            Map<String, MarkerDirective> directivesById,
            List<Diagnostic> diagnostics) {
        if (candidates.isEmpty()) {
            return;
        }
        candidates.sort(Comparator.comparing(OriginCandidate::rule, MarkerRule.EVALUATION_ORDER)
                .thenComparing(OriginCandidate::sourcePosition));
        OriginCandidate chosen = candidates.get(0);
        directivesById.put(chosen.directive().stableId(), chosen.directive());
        if (candidates.size() > 1) {
            diagnostics.add(diagnostic(
                    DiagnosticSeverity.WARNING,
                    MULTIPLE_ORIGINS,
                    "Multiple origin markers matched; priority, rule ID, and source position selected "
                            + chosen.directive().stableId(),
                    Optional.of(chosen.directive().sources().get(0)),
                    Map.of(DETAILS_STABLE_ID,
                            new MetadataValue.StringValue(chosen.directive().stableId()))));
        }
    }

    private static SourceReference sourceReference(
            StructureSnapshot snapshot,
            BlockEntry block,
            MarkerRule rule) {
        return new SourceReference(
                snapshot.snapshotId(),
                Optional.of(block.relativePosition()),
                Optional.of(block.blockId()),
                Optional.of(rule.ruleId()));
    }

    private static Vec3d markerPosition(
            BlockEntry block,
            Map<CanonicalIdentifier, MetadataValue> parameters) {
        BlockPosition position = block.relativePosition();
        return new Vec3d(
                position.x() + 0.5 + number(parameters, MarkerRule.Parameters.OFFSET_X, 0.0),
                position.y() + 0.5 + number(parameters, MarkerRule.Parameters.OFFSET_Y, 0.0),
                position.z() + 0.5 + number(parameters, MarkerRule.Parameters.OFFSET_Z, 0.0));
    }

    private static Transform markerTransform(
            BlockEntry block,
            Map<CanonicalIdentifier, MetadataValue> parameters) {
        Vec3d translation = markerPosition(block, parameters);
        double x = Math.toRadians(number(parameters, MarkerRule.Parameters.ROTATION_X_DEGREES, 0.0));
        double y = Math.toRadians(number(parameters, MarkerRule.Parameters.ROTATION_Y_DEGREES, 0.0));
        double z = Math.toRadians(number(parameters, MarkerRule.Parameters.ROTATION_Z_DEGREES, 0.0));
        return new Transform(translation, quaternionFromEuler(x, y, z), Vec3d.ONE);
    }

    private static Quaterniond quaternionFromEuler(double x, double y, double z) {
        double cx = Math.cos(x * 0.5);
        double sx = Math.sin(x * 0.5);
        double cy = Math.cos(y * 0.5);
        double sy = Math.sin(y * 0.5);
        double cz = Math.cos(z * 0.5);
        double sz = Math.sin(z * 0.5);
        return new Quaterniond(
                sx * cy * cz - cx * sy * sz,
                cx * sy * cz + sx * cy * sz,
                cx * cy * sz - sx * sy * cz,
                cx * cy * cz + sx * sy * sz);
    }

    private static CameraProjection parseProjection(String value) {
        return switch (value.trim().toLowerCase(Locale.ROOT)) {
            case "perspective" -> CameraProjection.PERSPECTIVE;
            case "orthographic" -> CameraProjection.ORTHOGRAPHIC;
            default -> throw new IllegalArgumentException("unknown camera projection: " + boundedMessage(value));
        };
    }

    private static CollisionKind parseCollisionKind(String value) {
        try {
            return CollisionKind.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("unknown collision kind: " + boundedMessage(value), exception);
        }
    }

    private static double number(
            Map<CanonicalIdentifier, MetadataValue> parameters,
            CanonicalIdentifier key,
            double defaultValue) {
        MetadataValue value = parameters.get(key);
        if (value == null) {
            return defaultValue;
        }
        return numericValue(value, key);
    }

    private static OptionalDouble optionalNumber(
            Map<CanonicalIdentifier, MetadataValue> parameters,
            CanonicalIdentifier key) {
        MetadataValue value = parameters.get(key);
        return value == null ? OptionalDouble.empty() : OptionalDouble.of(numericValue(value, key));
    }

    private static double numericValue(MetadataValue value, CanonicalIdentifier key) {
        double result;
        if (value instanceof MetadataValue.DoubleValue doubleValue) {
            result = doubleValue.value();
        } else if (value instanceof MetadataValue.LongValue longValue) {
            result = longValue.value();
        } else {
            throw new IllegalArgumentException("parameter " + key + " must be numeric");
        }
        if (!Double.isFinite(result)) {
            throw new IllegalArgumentException("parameter " + key + " must be finite");
        }
        return result;
    }

    private static long requiredLong(
            Map<CanonicalIdentifier, MetadataValue> parameters,
            CanonicalIdentifier key) {
        MetadataValue value = Objects.requireNonNull(
                parameters.get(key), "missing required parameter " + key);
        if (value instanceof MetadataValue.LongValue longValue) {
            return longValue.value();
        }
        throw new IllegalArgumentException("parameter " + key + " must be an integer");
    }

    private static boolean booleanValue(
            Map<CanonicalIdentifier, MetadataValue> parameters,
            CanonicalIdentifier key,
            boolean defaultValue) {
        MetadataValue value = parameters.get(key);
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof MetadataValue.BooleanValue booleanValue) {
            return booleanValue.value();
        }
        throw new IllegalArgumentException("parameter " + key + " must be boolean");
    }

    private static String requiredString(
            Map<CanonicalIdentifier, MetadataValue> parameters,
            CanonicalIdentifier key) {
        return optionalString(parameters, key)
                .orElseThrow(() -> new IllegalArgumentException("missing required parameter " + key));
    }

    private static Optional<String> optionalString(
            Map<CanonicalIdentifier, MetadataValue> parameters,
            CanonicalIdentifier key) {
        MetadataValue value = parameters.get(key);
        if (value == null) {
            return Optional.empty();
        }
        if (value instanceof MetadataValue.StringValue stringValue) {
            return Optional.of(stringValue.value());
        }
        throw new IllegalArgumentException("parameter " + key + " must be text");
    }

    private static String stableDirectiveId(MarkerRule rule, BlockEntry block) {
        BlockPosition position = block.relativePosition();
        return "marker/" + rule.action().type().name().toLowerCase(Locale.ROOT)
                + "/" + rule.ruleId().namespace() + "/" + rule.ruleId().path()
                + "/" + position.x() + "_" + position.y() + "_" + position.z();
    }

    private static String sanitizedName(String value, String fallback) {
        Objects.requireNonNull(value, "value");
        String cleaned = value.replaceAll("[\\p{Cntrl}]", "_")
                .replace('/', '_')
                .replace('\\', '_')
                .replace("..", "_")
                .replaceAll("[^\\p{L}\\p{N} ._-]", "_")
                .replaceAll("\\s+", " ")
                .trim();
        if (cleaned.isEmpty()) {
            cleaned = fallback;
        }
        return cleaned.length() <= 96 ? cleaned : cleaned.substring(0, 96);
    }

    private static String sanitizedStableId(String value) {
        Objects.requireNonNull(value, "value");
        String cleaned = value.replace("..", "_")
                .replaceAll("[^A-Za-z0-9._:/-]", "_")
                .replaceAll("_+", "_");
        while (!cleaned.isEmpty() && !Character.isLetterOrDigit(cleaned.charAt(0))) {
            cleaned = cleaned.substring(1);
        }
        if (cleaned.isEmpty()) {
            return "marker/unnamed";
        }
        return cleaned.length() <= 160 ? cleaned : cleaned.substring(0, 160);
    }

    private static Diagnostic diagnostic(
            DiagnosticSeverity severity,
            CanonicalIdentifier code,
            String message,
            Optional<SourceReference> source,
            Map<CanonicalIdentifier, MetadataValue> details) {
        return new Diagnostic(severity, code, boundedMessage(message), source, details);
    }

    private static String boundedMessage(String message) {
        String value = message == null ? "unspecified error" : message;
        value = value.replaceAll("[\\p{Cntrl}&&[^\\n\\t]]", "_");
        return value.length() <= 320 ? value : value.substring(0, 320);
    }

    private static CanonicalIdentifier code(String path) {
        return new CanonicalIdentifier("mcad", path);
    }

    private record ActionApplication(
            boolean successful,
            List<MarkerDirective> directives,
            Optional<CurvePointCandidate> curvePoint,
            Optional<OriginCandidate> origin,
            List<Diagnostic> diagnostics) {
        private ActionApplication {
            directives = List.copyOf(directives);
            Objects.requireNonNull(curvePoint, "curvePoint");
            Objects.requireNonNull(origin, "origin");
            diagnostics = List.copyOf(diagnostics);
        }

        private static ActionApplication of(MarkerDirective directive) {
            return new ActionApplication(true, List.of(directive), Optional.empty(), Optional.empty(), List.of());
        }
    }

    private record CurvePointCandidate(
            CanonicalIdentifier chainId,
            String name,
            long order,
            Vec3d position,
            boolean closed,
            SourceReference source,
            MarkerRule rule,
            BlockPosition sourcePosition) {
    }

    private record OriginCandidate(
            MarkerRule rule,
            BlockPosition sourcePosition,
            MarkerDirective.Origin directive) {
    }
}
