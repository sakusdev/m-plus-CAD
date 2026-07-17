/*
 * SPDX-License-Identifier: MPL-2.0
 */
package dev.sakus.mcad.minecraft.gui;

import dev.sakus.mcad.api.ProgressUpdate;
import org.junit.jupiter.api.Test;

import java.util.OptionalLong;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OperationProgressModelTest {
    @Test
    void exposesProgressAndCancellationState() {
        OperationProgressModel model = new OperationProgressModel();
        AtomicBoolean cancelled = new AtomicBoolean();

        model.begin("snapshot", OptionalLong.of(10L), "開始", true, () -> cancelled.set(true));
        OperationProgressModel.Snapshot progress =
                model.report("snapshot", 4L, OptionalLong.of(10L), "4/10");

        assertEquals(OperationProgressModel.State.RUNNING, progress.state());
        assertEquals(4L, progress.completed());
        assertTrue(model.requestCancellation());
        assertTrue(cancelled.get());
        assertEquals(OperationProgressModel.State.CANCELLING, model.snapshot().state());
        assertFalse(model.requestCancellation());

        OperationProgressModel.Snapshot finished = model.cancelled("キャンセル済み");
        assertEquals(OperationProgressModel.State.CANCELLED, finished.state());
    }

    @Test
    void bridgeImplementsNeutralProgressAndCancellationContracts() {
        OperationProgressModel model = new OperationProgressModel();
        OperationProgressBridge bridge = new OperationProgressBridge(model);
        bridge.begin("export", OptionalLong.of(5L), "開始", true);
        bridge.report(new ProgressUpdate("export", 2L, OptionalLong.of(5L), "2/5"));

        assertEquals(2L, model.snapshot().completed());
        assertFalse(bridge.isCancellationRequested());
        assertTrue(model.requestCancellation());
        assertTrue(bridge.isCancellationRequested());

        bridge.report(new ProgressUpdate("export", 3L, OptionalLong.of(5L), "late"));
        assertEquals(2L, model.snapshot().completed());
    }

    @Test
    void rejectsInvalidProgressAndConcurrentOperations() {
        OperationProgressModel model = new OperationProgressModel();
        model.begin("export", OptionalLong.of(2L), "開始", false, () -> { });

        assertThrows(IllegalArgumentException.class,
                () -> model.report("export", 3L, OptionalLong.of(2L), "invalid"));
        assertThrows(IllegalStateException.class,
                () -> model.begin("snapshot", OptionalLong.empty(), "開始", false, () -> { }));
    }
}
