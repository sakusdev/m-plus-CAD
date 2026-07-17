/*
 * SPDX-License-Identifier: MPL-2.0
 */
package dev.sakus.mcad.api;


import java.util.Optional;

public record ProducedFile(
        String relativePath,
        long sizeBytes,
        Optional<String> mediaType,
        Optional<String> sha256) implements Comparable<ProducedFile> {

    public ProducedFile {
        Checks.safeRelativePath(relativePath, "relativePath");
        Checks.nonNegative(sizeBytes, "sizeBytes");
        mediaType = Checks.notNull(mediaType, "mediaType");
        mediaType.ifPresent(value -> Checks.nonBlank(value, "mediaType"));
        sha256 = Checks.notNull(sha256, "sha256");
        sha256.ifPresent(value -> {
            if (!value.matches("[a-f0-9]{64}")) {
                throw new IllegalArgumentException("sha256 must be 64 lowercase hexadecimal characters");
            }
        });
    }

    @Override
    public int compareTo(ProducedFile other) {
        return relativePath.compareTo(other.relativePath);
    }
}
