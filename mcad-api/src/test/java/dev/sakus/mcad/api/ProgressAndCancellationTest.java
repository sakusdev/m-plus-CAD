/*
 * SPDX-License-Identifier: MPL-2.0
 */
package dev.sakus.mcad.api;


import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.OptionalLong;
import java.util.concurrent.CancellationException;
import org.junit.jupiter.api.Test;

class ProgressAndCancellationTest {
    @Test
    void progressRejectsImpossibleCounts() {
        assertThrows(IllegalArgumentException.class,
                () -> new ProgressUpdate("snapshot", 11, OptionalLong.of(10), "copying"));
    }

    @Test
    void cancellationTokenHasStandardThrowSurface() {
        CancellationToken token = () -> true;
        assertThrows(CancellationException.class, token::throwIfCancellationRequested);
    }
}
