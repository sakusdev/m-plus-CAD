/*
 * SPDX-License-Identifier: MPL-2.0
 */
package dev.sakus.mcad.api;


import java.util.regex.Pattern;

public record CanonicalIdentifier(String namespace, String path) implements Comparable<CanonicalIdentifier> {
    private static final Pattern NAMESPACE = Pattern.compile("[a-z0-9_.-]+");
    private static final Pattern PATH = Pattern.compile("[a-z0-9/._-]+");

    public CanonicalIdentifier {
        Checks.nonBlank(namespace, "namespace");
        Checks.nonBlank(path, "path");
        if (!NAMESPACE.matcher(namespace).matches()) {
            throw new IllegalArgumentException("invalid canonical namespace: " + namespace);
        }
        if (!PATH.matcher(path).matches()) {
            throw new IllegalArgumentException("invalid canonical path: " + path);
        }
    }

    public static CanonicalIdentifier parse(String value) {
        Checks.nonBlank(value, "identifier");
        int separator = value.indexOf(':');
        if (separator <= 0 || separator != value.lastIndexOf(':') || separator == value.length() - 1) {
            throw new IllegalArgumentException("identifier must be namespace:path: " + value);
        }
        return new CanonicalIdentifier(value.substring(0, separator), value.substring(separator + 1));
    }

    @Override
    public int compareTo(CanonicalIdentifier other) {
        int namespaceComparison = namespace.compareTo(other.namespace);
        return namespaceComparison != 0 ? namespaceComparison : path.compareTo(other.path);
    }

    @Override
    public String toString() {
        return namespace + ":" + path;
    }
}
