/*
 * SPDX-License-Identifier: MPL-2.0
 */
package dev.sakus.mcad.api;


public record UserAssetReference(String projectRelativePath, boolean copyOnExport) implements Comparable<UserAssetReference> {
    public UserAssetReference {
        Checks.safeRelativePath(projectRelativePath, "projectRelativePath");
    }

    @Override
    public int compareTo(UserAssetReference other) {
        int pathComparison = projectRelativePath.compareTo(other.projectRelativePath);
        return pathComparison != 0 ? pathComparison : Boolean.compare(copyOnExport, other.copyOnExport);
    }
}
