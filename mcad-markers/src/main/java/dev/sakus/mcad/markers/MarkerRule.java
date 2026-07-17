/*
 * SPDX-License-Identifier: MPL-2.0
 */
package dev.sakus.mcad.markers;

import dev.sakus.mcad.api.BlockEntry;
import dev.sakus.mcad.api.CanonicalIdentifier;
import dev.sakus.mcad.api.MetadataValue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;

/**
 * One stable, GUI-editable marker interpretation rule.
 *
 * <p>Higher priorities are evaluated first. Ties are resolved by ascending {@code ruleId}.</p>
 */
public record MarkerRule(
        CanonicalIdentifier ruleId,
        int priority,
        boolean enabled,
        MatchPredicate match,
        Action action,
        SourcePolicy sourcePolicy) {

    public static final Comparator<MarkerRule> EVALUATION_ORDER = Comparator
            .comparingInt(MarkerRule::priority)
            .reversed()
            .thenComparing(MarkerRule::ruleId);

    public MarkerRule {
        Objects.requireNonNull(ruleId, "ruleId");
        Objects.requireNonNull(match, "match");
        Objects.requireNonNull(action, "action");
        Objects.requireNonNull(sourcePolicy, "sourcePolicy");
    }

    public enum SourcePolicy {
        RETAIN_STOP(false, false),
        RETAIN_CONTINUE(false, true),
        CONSUME_STOP(true, false),
        CONSUME_CONTINUE(true, true);

        private final boolean consumesSource;
        private final boolean continuesEvaluation;

        SourcePolicy(boolean consumesSource, boolean continuesEvaluation) {
            this.consumesSource = consumesSource;
            this.continuesEvaluation = continuesEvaluation;
        }

        public boolean consumesSource() {
            return consumesSource;
        }

        public boolean continuesEvaluation() {
            return continuesEvaluation;
        }
    }

    public enum ActionType {
        ORIGIN,
        POINT_LIGHT,
        CAMERA,
        CURVE_POINT,
        BONE,
        GROUP,
        COLLISION,
        CUSTOM_PROPERTY,
        CUSTOM
    }

    /** Generic action plus namespaced neutral parameters, suitable for a GUI editor model. */
    public record Action(
            ActionType type,
            Optional<CanonicalIdentifier> customActionId,
            NavigableMap<CanonicalIdentifier, MetadataValue> parameters) {

        public Action(
                ActionType type,
                Optional<CanonicalIdentifier> customActionId,
                Map<CanonicalIdentifier, MetadataValue> parameters) {
            this(type, customActionId, immutableParameters(parameters));
        }

        public Action {
            Objects.requireNonNull(type, "type");
            customActionId = Objects.requireNonNull(customActionId, "customActionId");
            parameters = immutableParameters(parameters);
            if (type == ActionType.CUSTOM && customActionId.isEmpty()) {
                throw new IllegalArgumentException("CUSTOM action requires customActionId");
            }
            if (type != ActionType.CUSTOM && customActionId.isPresent()) {
                throw new IllegalArgumentException("customActionId is only valid for CUSTOM actions");
            }
        }

        private static NavigableMap<CanonicalIdentifier, MetadataValue> immutableParameters(
                Map<CanonicalIdentifier, MetadataValue> values) {
            Objects.requireNonNull(values, "parameters");
            var copy = new TreeMap<CanonicalIdentifier, MetadataValue>();
            for (var entry : values.entrySet()) {
                copy.put(
                        Objects.requireNonNull(entry.getKey(), "parameter key"),
                        Objects.requireNonNull(entry.getValue(), "parameter value"));
            }
            return Collections.unmodifiableNavigableMap(copy);
        }
    }

    public sealed interface MatchPredicate permits MatchPredicate.Any, MatchPredicate.BlockId,
            MatchPredicate.StateProperties, MatchPredicate.MetadataEquals, MatchPredicate.AllOf,
            MatchPredicate.AnyOf, MatchPredicate.Not {

        boolean matches(BlockEntry block);

        record Any() implements MatchPredicate {
            @Override
            public boolean matches(BlockEntry block) {
                Objects.requireNonNull(block, "block");
                return true;
            }
        }

        record BlockId(CanonicalIdentifier blockId) implements MatchPredicate {
            public BlockId {
                Objects.requireNonNull(blockId, "blockId");
            }

            @Override
            public boolean matches(BlockEntry block) {
                return blockId.equals(Objects.requireNonNull(block, "block").blockId());
            }
        }

        record StateProperties(NavigableMap<String, String> requiredProperties) implements MatchPredicate {
            public StateProperties(Map<String, String> requiredProperties) {
                this(immutableStrings(requiredProperties));
            }

            public StateProperties {
                requiredProperties = immutableStrings(requiredProperties);
                if (requiredProperties.isEmpty()) {
                    throw new IllegalArgumentException("requiredProperties must not be empty");
                }
            }

            @Override
            public boolean matches(BlockEntry block) {
                return Objects.requireNonNull(block, "block").stateProperties().entrySet()
                        .containsAll(requiredProperties.entrySet());
            }

            private static NavigableMap<String, String> immutableStrings(Map<String, String> values) {
                Objects.requireNonNull(values, "requiredProperties");
                var copy = new TreeMap<String, String>();
                for (var entry : values.entrySet()) {
                    String key = requireText(entry.getKey(), "state property key");
                    String value = requireText(entry.getValue(), "state property value");
                    copy.put(key, value);
                }
                return Collections.unmodifiableNavigableMap(copy);
            }
        }

        record MetadataEquals(CanonicalIdentifier key, MetadataValue expected) implements MatchPredicate {
            public MetadataEquals {
                Objects.requireNonNull(key, "key");
                Objects.requireNonNull(expected, "expected");
            }

            @Override
            public boolean matches(BlockEntry block) {
                return expected.equals(Objects.requireNonNull(block, "block").metadata().get(key));
            }
        }

        record AllOf(List<MatchPredicate> predicates) implements MatchPredicate {
            public AllOf {
                predicates = immutablePredicates(predicates, "predicates");
            }

            @Override
            public boolean matches(BlockEntry block) {
                Objects.requireNonNull(block, "block");
                return predicates.stream().allMatch(predicate -> predicate.matches(block));
            }
        }

        record AnyOf(List<MatchPredicate> predicates) implements MatchPredicate {
            public AnyOf {
                predicates = immutablePredicates(predicates, "predicates");
            }

            @Override
            public boolean matches(BlockEntry block) {
                Objects.requireNonNull(block, "block");
                return predicates.stream().anyMatch(predicate -> predicate.matches(block));
            }
        }

        record Not(MatchPredicate predicate) implements MatchPredicate {
            public Not {
                Objects.requireNonNull(predicate, "predicate");
            }

            @Override
            public boolean matches(BlockEntry block) {
                return !predicate.matches(Objects.requireNonNull(block, "block"));
            }
        }

        private static List<MatchPredicate> immutablePredicates(List<MatchPredicate> values, String name) {
            Objects.requireNonNull(values, name);
            if (values.isEmpty()) {
                throw new IllegalArgumentException(name + " must not be empty");
            }
            var copy = new ArrayList<MatchPredicate>(values.size());
            for (MatchPredicate value : values) {
                copy.add(Objects.requireNonNull(value, name + " element"));
            }
            return List.copyOf(copy);
        }
    }

    /** Standard parameter keys. Marker text remains ordinary data under these keys. */
    public static final class Parameters {
        public static final CanonicalIdentifier NAME = id("name");
        public static final CanonicalIdentifier OFFSET_X = id("offset_x");
        public static final CanonicalIdentifier OFFSET_Y = id("offset_y");
        public static final CanonicalIdentifier OFFSET_Z = id("offset_z");
        public static final CanonicalIdentifier ROTATION_X_DEGREES = id("rotation_x_degrees");
        public static final CanonicalIdentifier ROTATION_Y_DEGREES = id("rotation_y_degrees");
        public static final CanonicalIdentifier ROTATION_Z_DEGREES = id("rotation_z_degrees");
        public static final CanonicalIdentifier COLOUR_RED = id("colour_red");
        public static final CanonicalIdentifier COLOUR_GREEN = id("colour_green");
        public static final CanonicalIdentifier COLOUR_BLUE = id("colour_blue");
        public static final CanonicalIdentifier INTENSITY = id("intensity");
        public static final CanonicalIdentifier RANGE = id("range");
        public static final CanonicalIdentifier PROJECTION = id("projection");
        public static final CanonicalIdentifier NEAR_PLANE = id("near_plane");
        public static final CanonicalIdentifier FAR_PLANE = id("far_plane");
        public static final CanonicalIdentifier FIELD_OF_VIEW_RADIANS = id("field_of_view_radians");
        public static final CanonicalIdentifier ORTHOGRAPHIC_HEIGHT = id("orthographic_height");
        public static final CanonicalIdentifier CHAIN_ID = id("chain_id");
        public static final CanonicalIdentifier CHAIN_ORDER = id("chain_order");
        public static final CanonicalIdentifier CLOSED = id("closed");
        public static final CanonicalIdentifier PARENT_ID = id("parent_id");
        public static final CanonicalIdentifier COLLISION_KIND = id("collision_kind");
        public static final CanonicalIdentifier TARGET_GROUP = id("target_group");
        public static final CanonicalIdentifier PROPERTY_KEY = id("property_key");
        public static final CanonicalIdentifier PROPERTY_VALUE = id("property_value");

        private Parameters() {
        }

        private static CanonicalIdentifier id(String path) {
            return new CanonicalIdentifier("mcad", path);
        }
    }

    private static String requireText(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isBlank() || value.indexOf('\0') >= 0) {
            throw new IllegalArgumentException(name + " must be non-blank and contain no NUL");
        }
        return value;
    }
}
