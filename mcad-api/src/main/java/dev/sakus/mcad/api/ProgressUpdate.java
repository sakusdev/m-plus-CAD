/*
 * SPDX-License-Identifier: MPL-2.0
 */
package dev.sakus.mcad.api;


import java.util.OptionalLong;

public record ProgressUpdate(String phase, long completed, OptionalLong total, String message) {
    public ProgressUpdate {
        Checks.stableId(phase, "phase");
        Checks.nonNegative(completed, "completed");
        total = Checks.notNull(total, "total");
        if (total.isPresent()) {
            Checks.nonNegative(total.getAsLong(), "total");
            if (completed > total.getAsLong()) {
                throw new IllegalArgumentException("completed must not exceed total");
            }
        }
        Checks.notNull(message, "message");
    }
}
