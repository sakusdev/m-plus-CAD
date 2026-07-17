/*
 * SPDX-License-Identifier: MPL-2.0
 */
package dev.sakus.mcad.api;


@FunctionalInterface
public interface ProgressReporter {
    ProgressReporter NONE = update -> { };

    void report(ProgressUpdate update);
}
