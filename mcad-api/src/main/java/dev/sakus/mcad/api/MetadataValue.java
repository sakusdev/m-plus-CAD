/*
 * SPDX-License-Identifier: MPL-2.0
 */
package dev.sakus.mcad.api;


import java.util.List;
import java.util.Map;
import java.util.NavigableMap;

public sealed interface MetadataValue permits MetadataValue.StringValue, MetadataValue.LongValue,
        MetadataValue.DoubleValue, MetadataValue.BooleanValue, MetadataValue.ListValue, MetadataValue.MapValue {

    record StringValue(String value) implements MetadataValue {
        public StringValue {
            Checks.notNull(value, "value");
            if (value.indexOf('\0') >= 0) {
                throw new IllegalArgumentException("metadata string must not contain NUL");
            }
        }
    }

    record LongValue(long value) implements MetadataValue {
    }

    record DoubleValue(double value) implements MetadataValue {
        public DoubleValue {
            Checks.finite(value, "value");
        }
    }

    record BooleanValue(boolean value) implements MetadataValue {
    }

    record ListValue(List<MetadataValue> values) implements MetadataValue {
        public ListValue {
            values = Checks.immutableList(values, "values");
        }
    }

    record MapValue(NavigableMap<CanonicalIdentifier, MetadataValue> values) implements MetadataValue {
        public MapValue(Map<CanonicalIdentifier, MetadataValue> values) {
            this(Checks.immutableSortedMap(values, CanonicalIdentifier::compareTo, "values"));
        }

        public MapValue {
            values = Checks.immutableSortedMap(values, CanonicalIdentifier::compareTo, "values");
        }
    }
}
