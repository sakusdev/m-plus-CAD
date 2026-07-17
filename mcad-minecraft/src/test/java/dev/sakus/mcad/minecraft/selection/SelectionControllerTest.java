/*
 * SPDX-License-Identifier: MPL-2.0
 */
package dev.sakus.mcad.minecraft.selection;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SelectionControllerTest {
    @Test
    void setsReplacesAndClearsBothCorners() {
        SelectionController controller = new SelectionController(100L);

        SelectionController.Snapshot start = controller.setCorner(
                TwoPointSelection.Corner.START,
                new SelectionPoint(1, 2, 3));
        assertEquals(1L, start.revision());
        assertEquals(SelectionValidation.Code.INCOMPLETE, start.validation().code());

        SelectionController.Snapshot completed = controller.setCorner(
                TwoPointSelection.Corner.END,
                new SelectionPoint(2, 3, 4));
        assertEquals(2L, completed.revision());
        assertTrue(completed.validation().valid());
        assertEquals(8L, completed.validation().bounds().orElseThrow().blockCount().longValueExact());

        SelectionController.Snapshot replaced = controller.setCorner(
                TwoPointSelection.Corner.START,
                new SelectionPoint(-10, -10, -10));
        assertEquals(3L, replaced.revision());
        assertEquals(new SelectionPoint(-10, -10, -10), replaced.selection().start().orElseThrow());

        SelectionController.Snapshot oneCorner = controller.clearCorner(TwoPointSelection.Corner.END);
        assertEquals(SelectionValidation.Code.INCOMPLETE, oneCorner.validation().code());

        SelectionController.Snapshot cleared = controller.clear();
        assertTrue(cleared.selection().start().isEmpty());
        assertTrue(cleared.selection().end().isEmpty());
    }

    @Test
    void reportsOversizedSelectionAndRevalidatesWhenLimitChanges() {
        SelectionController controller = new SelectionController(7L);
        controller.setCorner(TwoPointSelection.Corner.START, new SelectionPoint(0, 0, 0));
        SelectionController.Snapshot oversized = controller.setCorner(
                TwoPointSelection.Corner.END,
                new SelectionPoint(1, 1, 1));

        assertEquals(SelectionValidation.Code.TOO_LARGE, oversized.validation().code());
        assertFalse(oversized.validation().suggestedAction().isBlank());

        SelectionController.Snapshot valid = controller.setMaximumBlockCount(8L);
        assertEquals(SelectionValidation.Code.VALID, valid.validation().code());
    }

    @Test
    void publishesImmutableSnapshotsInRevisionOrder() {
        SelectionController controller = new SelectionController(100L);
        List<Long> revisions = new ArrayList<>();
        try (SelectionController.Subscription ignored = controller.subscribe(
                snapshot -> revisions.add(snapshot.revision()))) {
            controller.setCorner(TwoPointSelection.Corner.START, new SelectionPoint(0, 0, 0));
            controller.setCorner(TwoPointSelection.Corner.END, new SelectionPoint(0, 0, 0));
        }
        controller.clear();

        assertEquals(List.of(1L, 2L), revisions);
    }

    @Test
    void reentrantUpdatesAreDeliveredAfterCurrentSnapshotToEveryListener() {
        SelectionController controller = new SelectionController(100L);
        List<String> events = new ArrayList<>();
        controller.subscribe(snapshot -> {
            events.add("first-" + snapshot.revision());
            if (snapshot.revision() == 1L) {
                controller.setCorner(TwoPointSelection.Corner.END, new SelectionPoint(1, 1, 1));
            }
        });
        controller.subscribe(snapshot -> events.add("second-" + snapshot.revision()));

        controller.setCorner(TwoPointSelection.Corner.START, new SelectionPoint(0, 0, 0));

        assertEquals(List.of("first-1", "second-1", "first-2", "second-2"), events);
    }

    @Test
    void oneListenerFailureDoesNotPreventOtherListenersOrStateUpdate() {
        SelectionController controller = new SelectionController(100L);
        List<Long> received = new ArrayList<>();
        controller.subscribe(snapshot -> {
            throw new IllegalStateException("listener failed");
        });
        controller.subscribe(snapshot -> received.add(snapshot.revision()));

        assertThrows(IllegalStateException.class, () -> controller.setCorner(
                TwoPointSelection.Corner.START,
                new SelectionPoint(0, 0, 0)));

        assertEquals(1L, controller.snapshot().revision());
        assertEquals(List.of(1L), received);
    }
}
