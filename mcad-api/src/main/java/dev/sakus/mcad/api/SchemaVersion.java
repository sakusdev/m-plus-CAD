/*
 * SPDX-License-Identifier: MPL-2.0
 */
package dev.sakus.mcad.api;


public record SchemaVersion(int major, int minor) implements Comparable<SchemaVersion> {
    public SchemaVersion {
        if (major < 0 || minor < 0) {
            throw new IllegalArgumentException("schema version components must be non-negative");
        }
    }

    @Override
    public int compareTo(SchemaVersion other) {
        int majorComparison = Integer.compare(major, other.major);
        return majorComparison != 0 ? majorComparison : Integer.compare(minor, other.minor);
    }

    @Override
    public String toString() {
        return major + "." + minor;
    }
}
