/*
 * SPDX-License-Identifier: MPL-2.0
 */
package dev.sakus.mcad.markers;

import dev.sakus.mcad.api.BlockEntry;
import dev.sakus.mcad.api.BlockPosition;
import dev.sakus.mcad.api.CanonicalIdentifier;
import dev.sakus.mcad.api.IntSize3;
import dev.sakus.mcad.api.MetadataValue;
import dev.sakus.mcad.api.SchemaVersion;
import dev.sakus.mcad.api.StructureSnapshot;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CancellationException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ConfigurableMarkerInterpreterTest {
    private static final CanonicalIdentifier STONE = id("test:stone");
    private static final CanonicalIdentifier GOLD = id("test:gold");

    @Test
    void interpretsOriginMarkerAndConsumesSource() {
        StructureSnapshot snapshot = snapshot(block(0, STONE));
        MarkerRule rule = rule(
                "test:origin",
                100,
                new MarkerRule.MatchPredicate.BlockId(STONE),
                MarkerRule.ActionType.ORIGIN,
                Map.of(),
                MarkerRule.SourcePolicy.CONSUME_STOP);

        MarkerInterpretationResult result = new ConfigurableMarkerInterpreter()
                .interpret(snapshot, rules(rule));

        assertTrue(result.retainedBlocks().isEmpty());
        assertEquals(List.of(new BlockPosition(0, 0, 0)), result.consumedPositions());
        MarkerDirective.Origin origin = assertInstanceOf(MarkerDirective.Origin.class, result.directives().get(0));
        assertEquals(0.5, origin.position().x());
        assertEquals(id("test:origin"), origin.sources().get(0).markerRuleId().orElseThrow());
    }

    @Test
    void diagnosesConflictingRulesAndUsesStableRuleOrder() {
        MarkerRule laterId = rule(
                "test:z_rule",
                10,
                new MarkerRule.MatchPredicate.BlockId(STONE),
                MarkerRule.ActionType.GROUP,
                Map.of(MarkerRule.Parameters.NAME, new MetadataValue.StringValue("Z")),
                MarkerRule.SourcePolicy.RETAIN_CONTINUE);
        MarkerRule earlierId = rule(
                "test:a_rule",
                10,
                new MarkerRule.MatchPredicate.BlockId(STONE),
                MarkerRule.ActionType.GROUP,
                Map.of(MarkerRule.Parameters.NAME, new MetadataValue.StringValue("A")),
                MarkerRule.SourcePolicy.RETAIN_CONTINUE);

        MarkerInterpretationResult result = new ConfigurableMarkerInterpreter()
                .interpret(snapshot(block(0, STONE)), rules(laterId, earlierId));

        assertTrue(result.diagnostics().stream()
                .anyMatch(diagnostic -> diagnostic.code().equals(id("mcad:marker_rule_conflict"))));
        assertEquals(id("test:a_rule"), result.applications().get(0).ruleId());
        assertEquals(id("test:z_rule"), result.applications().get(1).ruleId());
    }

    @Test
    void distinguishesConsumedAndRetainedSources() {
        MarkerRule consume = rule(
                "test:consume",
                1,
                new MarkerRule.MatchPredicate.BlockId(STONE),
                MarkerRule.ActionType.GROUP,
                Map.of(),
                MarkerRule.SourcePolicy.CONSUME_STOP);
        MarkerRule retain = rule(
                "test:retain",
                1,
                new MarkerRule.MatchPredicate.BlockId(GOLD),
                MarkerRule.ActionType.GROUP,
                Map.of(),
                MarkerRule.SourcePolicy.RETAIN_STOP);

        MarkerInterpretationResult result = new ConfigurableMarkerInterpreter()
                .interpret(snapshot(block(1, GOLD), block(0, STONE)), rules(retain, consume));

        assertEquals(List.of(new BlockPosition(0, 0, 0)), result.consumedPositions());
        assertEquals(List.of(new BlockPosition(1, 0, 0)),
                result.retainedBlocks().stream().map(BlockEntry::relativePosition).toList());
    }

    @Test
    void invalidParametersProduceDiagnosticWithoutConsumingSource() {
        MarkerRule rule = rule(
                "test:bad_light",
                1,
                new MarkerRule.MatchPredicate.BlockId(STONE),
                MarkerRule.ActionType.POINT_LIGHT,
                Map.of(MarkerRule.Parameters.INTENSITY, new MetadataValue.StringValue("not-a-number")),
                MarkerRule.SourcePolicy.CONSUME_STOP);

        MarkerInterpretationResult result = new ConfigurableMarkerInterpreter()
                .interpret(snapshot(block(0, STONE)), rules(rule));

        assertEquals(1, result.retainedBlocks().size());
        assertTrue(result.consumedPositions().isEmpty());
        assertTrue(result.directives().isEmpty());
        assertTrue(result.diagnostics().stream()
                .anyMatch(diagnostic -> diagnostic.code().equals(id("mcad:invalid_marker_parameters"))));
    }

    @Test
    void ordersCurvePointsByExplicitOrderThenStableTieBreakers() {
        CanonicalIdentifier chain = id("test:path");
        MarkerRule second = curveRule("test:second", GOLD, chain, 20);
        MarkerRule first = curveRule("test:first", STONE, chain, 10);

        MarkerInterpretationResult result = new ConfigurableMarkerInterpreter()
                .interpret(snapshot(block(1, GOLD), block(0, STONE)), rules(second, first));

        MarkerDirective.Curve curve = assertInstanceOf(MarkerDirective.Curve.class, result.directives().get(0));
        assertEquals(List.of(0.5, 1.5), curve.controlPoints().stream().map(point -> point.x()).toList());
        assertEquals(List.of(new BlockPosition(0, 0, 0), new BlockPosition(1, 0, 0)),
                curve.sources().stream().map(source -> source.relativeBlockPosition().orElseThrow()).toList());
    }

    @Test
    void unknownCustomActionProducesDiagnosticAndRetainsSource() {
        MarkerRule rule = new MarkerRule(
                id("test:custom_rule"),
                1,
                true,
                new MarkerRule.MatchPredicate.BlockId(STONE),
                new MarkerRule.Action(
                        MarkerRule.ActionType.CUSTOM,
                        Optional.of(id("test:missing_action")),
                        Map.of()),
                MarkerRule.SourcePolicy.CONSUME_STOP);

        MarkerInterpretationResult result = new ConfigurableMarkerInterpreter()
                .interpret(snapshot(block(0, STONE)), rules(rule));

        assertEquals(1, result.retainedBlocks().size());
        assertTrue(result.consumedPositions().isEmpty());
        assertTrue(result.diagnostics().stream()
                .anyMatch(diagnostic -> diagnostic.code().equals(id("mcad:unknown_custom_marker_action"))));
    }

    @Test
    void unsafeMarkerTextIsSanitizedAndNeverInterpretedAsCodeOrPath() {
        String unsafe = "../../evil\\name\u0001<script>alert(1)</script>";
        MarkerRule rule = rule(
                "test:unsafe_name",
                1,
                new MarkerRule.MatchPredicate.BlockId(STONE),
                MarkerRule.ActionType.GROUP,
                Map.of(MarkerRule.Parameters.NAME, new MetadataValue.StringValue(unsafe)),
                MarkerRule.SourcePolicy.RETAIN_STOP);

        MarkerInterpretationResult result = new ConfigurableMarkerInterpreter()
                .interpret(snapshot(block(0, STONE)), rules(rule));

        MarkerDirective.Group group = assertInstanceOf(MarkerDirective.Group.class, result.directives().get(0));
        assertFalse(group.name().contains("/"));
        assertFalse(group.name().contains("\\"));
        assertFalse(group.name().contains(".."));
        assertTrue(group.name().chars().noneMatch(character -> Character.isISOControl(character)));
        assertFalse(group.name().contains("<script>"));
    }

    @Test
    void cancellationReturnsNoPartialResult() {
        MarkerRule rule = rule(
                "test:cancel",
                1,
                new MarkerRule.MatchPredicate.Any(),
                MarkerRule.ActionType.GROUP,
                Map.of(),
                MarkerRule.SourcePolicy.RETAIN_STOP);

        assertThrows(CancellationException.class, () -> new ConfigurableMarkerInterpreter().interpret(
                snapshot(block(0, STONE)), rules(rule), () -> true, update -> { }));
    }

    private static MarkerRule curveRule(
            String ruleId,
            CanonicalIdentifier blockId,
            CanonicalIdentifier chainId,
            long order) {
        return rule(
                ruleId,
                1,
                new MarkerRule.MatchPredicate.BlockId(blockId),
                MarkerRule.ActionType.CURVE_POINT,
                Map.of(
                        MarkerRule.Parameters.CHAIN_ID, new MetadataValue.StringValue(chainId.toString()),
                        MarkerRule.Parameters.CHAIN_ORDER, new MetadataValue.LongValue(order)),
                MarkerRule.SourcePolicy.CONSUME_STOP);
    }

    private static MarkerRule rule(
            String ruleId,
            int priority,
            MarkerRule.MatchPredicate predicate,
            MarkerRule.ActionType actionType,
            Map<CanonicalIdentifier, MetadataValue> parameters,
            MarkerRule.SourcePolicy sourcePolicy) {
        return new MarkerRule(
                id(ruleId),
                priority,
                true,
                predicate,
                new MarkerRule.Action(actionType, Optional.empty(), parameters),
                sourcePolicy);
    }

    private static MarkerRuleSet rules(MarkerRule... rules) {
        return new MarkerRuleSet(MarkerRuleSet.CURRENT_SCHEMA_VERSION, List.of(rules));
    }

    private static StructureSnapshot snapshot(BlockEntry... blocks) {
        return new StructureSnapshot(
                new SchemaVersion(1, 0),
                "snapshot/test",
                new IntSize3(4, 4, 4),
                Optional.empty(),
                List.of(blocks),
                Map.of());
    }

    private static BlockEntry block(int x, CanonicalIdentifier blockId) {
        return new BlockEntry(new BlockPosition(x, 0, 0), blockId, Map.of(), Map.of());
    }

    private static CanonicalIdentifier id(String value) {
        return CanonicalIdentifier.parse(value);
    }
}
