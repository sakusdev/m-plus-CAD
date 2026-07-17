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
    void exposesProgressCancellationAndMonotonicRevisions() {
        OperationProgressModel model = new OperationProgressModel();
        AtomicBoolean cancelled = new AtomicBoolean();

        OperationProgressModel.Snapshot started =
                model.begin("snapshot", OptionalLong.of(10L), "開始", true, () -> cancelled.set(true));
        OperationProgressModel.Snapshot progress =
                model.report("snapshot", 4L, OptionalLong.of(10L), "4/10");

        assertEquals(1L, started.revision());
        assertEquals(2L, progress.revision());
        assertEquals(OperationProgressModel.State.RUNNING, progress.state());
        assertEquals(4L, progress.completed());
        assertTrue(model.requestCancellation());
        assertTrue(cancelled.get());
        assertEquals(3L, model.snapshot().revision());
        assertEquals(OperationProgressModel.State.CANCELLING, model.snapshot().state());
        assertFalse(model.requestCancellation());

        OperationProgressModel.Snapshot finished = model.cancelled("キャンセル済み");
        assertEquals(4L, finished.revision());
        assertEquals(OperationProgressModel.State.CANCELLED, finished.state());
        OperationProgressModel.Snapshot reset = model.reset();
        assertEquals(5L, reset.revision());
        assertEquals(OperationProgressModel.State.IDLE, reset.state());
        assertEquals(5L, model.reset().revision());
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
    void failedSecondBeginDoesNotClearExistingCancellation() {
        OperationProgressModel model = new OperationProgressModel();
        OperationProgressBridge bridge = new OperationProgressBridge(model);
        bridge.begin("export", OptionalLong.empty(), "開始", true);
        assertTrue(model.requestCancellation());
        assertTrue(bridge.isCancellationRequested());

        assertThrows(IllegalStateException.class,
                () -> bridge.begin("snapshot", OptionalLong.empty(), "重複開始", true));

        assertTrue(bridge.isCancellationRequested());
        assertEquals(OperationProgressModel.State.CANCELLING, model.snapshot().state());
    }

    @Test
    void rejectsBackwardProgressWithinPhaseButAllowsNewPhaseToRestart() {
        OperationProgressModel model = new OperationProgressModel();
        model.begin("snapshot.read", OptionalLong.of(10L), "読取", false, () -> { });
        model.report("snapshot.read", 5L, OptionalLong.of(10L), "5/10");

        assertThrows(IllegalArgumentException.class,
                () -> model.report("snapshot.read", 4L, OptionalLong.of(10L), "4/10"));

        OperationProgressModel.Snapshot nextPhase =
                model.report("snapshot.convert", 0L, OptionalLong.of(2L), "変換開始");
        assertEquals(0L, nextPhase.completed());
        assertEquals("snapshot.convert", nextPhase.phase());
    }

    @Test
    void rejectsInvalidProgressConcurrentOperationsAndBlankTerminalMessages() {
        OperationProgressModel model = new OperationProgressModel();
        model.begin("export", OptionalLong.of(2L), "開始", false, () -> { });

        assertThrows(IllegalArgumentException.class,
                () -> model.report("export", 3L, OptionalLong.of(2L), "invalid"));
        assertThrows(IllegalStateException.class,
                () -> model.begin("snapshot", OptionalLong.empty(), "開始", false, () -> { }));
        assertThrows(IllegalArgumentException.class, () -> model.succeed(" "));
    }
}
