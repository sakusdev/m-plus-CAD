/*
 * SPDX-License-Identifier: MPL-2.0
 */
package dev.sakus.mcad.markers;

import dev.sakus.mcad.api.CanonicalIdentifier;
import dev.sakus.mcad.api.SchemaVersion;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;

/** Versioned, deterministic marker-rule configuration edited by the GUI layer. */
public record MarkerRuleSet(SchemaVersion schemaVersion, List<MarkerRule> rules) {
    public static final SchemaVersion CURRENT_SCHEMA_VERSION = new SchemaVersion(1, 0);

    public MarkerRuleSet {
        Objects.requireNonNull(schemaVersion, "schemaVersion");
        Objects.requireNonNull(rules, "rules");
        var copy = new ArrayList<MarkerRule>(rules.size());
        var ids = new HashSet<CanonicalIdentifier>();
        for (MarkerRule rule : rules) {
            MarkerRule checked = Objects.requireNonNull(rule, "rule");
            if (!ids.add(checked.ruleId())) {
                throw new IllegalArgumentException("duplicate marker rule ID: " + checked.ruleId());
            }
            copy.add(checked);
        }
        copy.sort(MarkerRule.EVALUATION_ORDER);
        rules = List.copyOf(copy);
    }
}
