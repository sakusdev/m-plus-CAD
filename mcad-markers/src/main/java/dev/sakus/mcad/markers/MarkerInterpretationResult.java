/*
 * SPDX-License-Identifier: MPL-2.0
 */
package dev.sakus.mcad.markers;

import dev.sakus.mcad.api.BlockEntry;
import dev.sakus.mcad.api.BlockPosition;
import dev.sakus.mcad.api.CanonicalIdentifier;
import dev.sakus.mcad.api.Diagnostic;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;

/** Immutable result of applying marker rules to one detached snapshot. */
public record MarkerInterpretationResult(
        List<BlockEntry> retainedBlocks,
        List<BlockPosition> consumedPositions,
        List<MarkerDirective> directives,
        List<Diagnostic> diagnostics,
        List<RuleApplication> applications) {

    private static final Comparator<Diagnostic> DIAGNOSTIC_ORDER = Diagnostic.STABLE_ORDER
            .thenComparing(diagnostic -> diagnostic.details().toString());

    public MarkerInterpretationResult {
        retainedBlocks = immutableSortedBlocks(retainedBlocks);
        consumedPositions = immutableSortedPositions(consumedPositions);
        directives = immutableSortedDirectives(directives);
        diagnostics = immutableSortedDiagnostics(diagnostics);
        applications = immutableSortedApplications(applications);

        var retained = new HashSet<BlockPosition>();
        for (BlockEntry block : retainedBlocks) {
            retained.add(block.relativePosition());
        }
        for (BlockPosition consumed : consumedPositions) {
            if (retained.contains(consumed)) {
                throw new IllegalArgumentException("position cannot be both retained and consumed: " + consumed);
            }
        }
    }

    public boolean isConsumed(BlockPosition position) {
        return consumedPositions.contains(Objects.requireNonNull(position, "position"));
    }

    public record RuleApplication(
            BlockPosition sourcePosition,
            CanonicalIdentifier ruleId,
            boolean consumedSource,
            List<String> directiveIds) {
        public RuleApplication {
            Objects.requireNonNull(sourcePosition, "sourcePosition");
            Objects.requireNonNull(ruleId, "ruleId");
            Objects.requireNonNull(directiveIds, "directiveIds");
            var copy = new ArrayList<String>(directiveIds.size());
            for (String directiveId : directiveIds) {
                String checked = Objects.requireNonNull(directiveId, "directiveId");
                if (checked.isBlank() || checked.indexOf('\0') >= 0) {
                    throw new IllegalArgumentException("directiveId must be non-blank and contain no NUL");
                }
                copy.add(checked);
            }
            copy.sort(String::compareTo);
            directiveIds = List.copyOf(copy);
        }
    }

    private static List<BlockEntry> immutableSortedBlocks(List<BlockEntry> values) {
        Objects.requireNonNull(values, "retainedBlocks");
        var copy = new ArrayList<BlockEntry>(values.size());
        var positions = new HashSet<BlockPosition>();
        for (BlockEntry value : values) {
            BlockEntry checked = Objects.requireNonNull(value, "retained block");
            if (!positions.add(checked.relativePosition())) {
                throw new IllegalArgumentException("duplicate retained block position: " + checked.relativePosition());
            }
            copy.add(checked);
        }
        copy.sort(Comparator.comparing(BlockEntry::relativePosition));
        return List.copyOf(copy);
    }

    private static List<BlockPosition> immutableSortedPositions(List<BlockPosition> values) {
        Objects.requireNonNull(values, "consumedPositions");
        var copy = new ArrayList<BlockPosition>(values.size());
        var unique = new HashSet<BlockPosition>();
        for (BlockPosition value : values) {
            BlockPosition checked = Objects.requireNonNull(value, "consumed position");
            if (!unique.add(checked)) {
                throw new IllegalArgumentException("duplicate consumed position: " + checked);
            }
            copy.add(checked);
        }
        copy.sort(BlockPosition::compareTo);
        return List.copyOf(copy);
    }

    private static List<MarkerDirective> immutableSortedDirectives(List<MarkerDirective> values) {
        Objects.requireNonNull(values, "directives");
        var copy = new ArrayList<MarkerDirective>(values.size());
        var ids = new HashSet<String>();
        for (MarkerDirective value : values) {
            MarkerDirective checked = Objects.requireNonNull(value, "directive");
            if (!ids.add(checked.stableId())) {
                throw new IllegalArgumentException("duplicate directive stableId: " + checked.stableId());
            }
            copy.add(checked);
        }
        copy.sort(Comparator.comparing(MarkerDirective::stableId));
        return List.copyOf(copy);
    }

    private static List<Diagnostic> immutableSortedDiagnostics(List<Diagnostic> values) {
        Objects.requireNonNull(values, "diagnostics");
        var copy = new ArrayList<Diagnostic>(values.size());
        for (Diagnostic value : values) {
            copy.add(Objects.requireNonNull(value, "diagnostic"));
        }
        copy.sort(DIAGNOSTIC_ORDER);
        return List.copyOf(copy);
    }

    private static List<RuleApplication> immutableSortedApplications(List<RuleApplication> values) {
        Objects.requireNonNull(values, "applications");
        var copy = new ArrayList<RuleApplication>(values.size());
        for (RuleApplication value : values) {
            copy.add(Objects.requireNonNull(value, "application"));
        }
        copy.sort(Comparator.comparing(RuleApplication::sourcePosition)
                .thenComparing(RuleApplication::ruleId));
        return List.copyOf(copy);
    }
}
