/*
 * SPDX-License-Identifier: MPL-2.0
 */
package dev.sakus.mcad.api;


import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.NavigableSet;
import java.util.Objects;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Pattern;

final class Checks {
    private static final Pattern STABLE_ID = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._:/-]*");

    private Checks() {
    }

    static <T> T notNull(T value, String name) {
        return Objects.requireNonNull(value, name);
    }

    static String nonBlank(String value, String name) {
        notNull(value, name);
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        if (value.indexOf('\0') >= 0) {
            throw new IllegalArgumentException(name + " must not contain NUL");
        }
        return value;
    }

    static String stableId(String value, String name) {
        nonBlank(value, name);
        if (!STABLE_ID.matcher(value).matches()) {
            throw new IllegalArgumentException(name + " contains unsupported characters: " + value);
        }
        return value;
    }

    static double finite(double value, String name) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException(name + " must be finite");
        }
        return value;
    }

    static double range(double value, double min, double max, String name) {
        finite(value, name);
        if (value < min || value > max) {
            throw new IllegalArgumentException(name + " must be in [" + min + ", " + max + "]");
        }
        return value;
    }

    static long nonNegative(long value, String name) {
        if (value < 0) {
            throw new IllegalArgumentException(name + " must be non-negative");
        }
        return value;
    }

    static int positive(int value, String name) {
        if (value <= 0) {
            throw new IllegalArgumentException(name + " must be positive");
        }
        return value;
    }

    static long positive(long value, String name) {
        if (value <= 0) {
            throw new IllegalArgumentException(name + " must be positive");
        }
        return value;
    }

    static <T> List<T> immutableList(Collection<? extends T> values, String name) {
        notNull(values, name);
        return List.copyOf(values);
    }

    static <T> List<T> immutableSortedList(
            Collection<? extends T> values,
            Comparator<? super T> comparator,
            String name) {
        notNull(values, name);
        var copy = new ArrayList<T>(values);
        copy.sort(comparator);
        return List.copyOf(copy);
    }

    static <T> List<T> immutableDistinctSortedList(
            Collection<? extends T> values,
            Comparator<? super T> comparator,
            String name) {
        var copy = immutableSortedList(values, comparator, name);
        requireNoDuplicates(copy, name);
        return copy;
    }

    static List<String> immutableDistinctSortedStrings(Collection<String> values, String name) {
        notNull(values, name);
        var sorted = new TreeSet<String>();
        for (String value : values) {
            sorted.add(stableId(value, name + " element"));
        }
        if (sorted.size() != values.size()) {
            throw new IllegalArgumentException(name + " must not contain duplicates");
        }
        return List.copyOf(sorted);
    }

    static <K, V> NavigableMap<K, V> immutableSortedMap(
            Map<? extends K, ? extends V> values,
            Comparator<? super K> comparator,
            String name) {
        notNull(values, name);
        var copy = new TreeMap<K, V>(comparator);
        for (var entry : values.entrySet()) {
            copy.put(notNull(entry.getKey(), name + " key"), notNull(entry.getValue(), name + " value"));
        }
        return Collections.unmodifiableNavigableMap(copy);
    }

    static <T> NavigableSet<T> immutableSortedSet(
            Collection<? extends T> values,
            Comparator<? super T> comparator,
            String name) {
        notNull(values, name);
        var copy = new TreeSet<T>(comparator);
        copy.addAll(values);
        if (copy.size() != values.size()) {
            throw new IllegalArgumentException(name + " must not contain duplicates");
        }
        return Collections.unmodifiableNavigableSet(copy);
    }

    static <T> void requireNoDuplicates(Collection<T> values, String name) {
        if (new LinkedHashSet<>(values).size() != values.size()) {
            throw new IllegalArgumentException(name + " must not contain duplicates");
        }
    }

    static String safeRelativePath(String value, String name) {
        nonBlank(value, name);
        if (value.startsWith("/") || value.startsWith("\\") || value.contains("\\") || value.contains(":")) {
            throw new IllegalArgumentException(name + " must be a portable relative path");
        }
        String[] parts = value.split("/", -1);
        for (String part : parts) {
            if (part.isEmpty() || part.equals(".") || part.equals("..")) {
                throw new IllegalArgumentException(name + " contains an unsafe path segment");
            }
        }
        return value;
    }
}
