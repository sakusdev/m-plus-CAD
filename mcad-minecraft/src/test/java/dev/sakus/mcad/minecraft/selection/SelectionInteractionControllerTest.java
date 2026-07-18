/*
 * SPDX-License-Identifier: MPL-2.0
 */
package dev.sakus.mcad.minecraft.selection;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SelectionInteractionControllerTest {
    @Test
    void setActionsApplyTargetedPrimitiveCoordinates() {
        SelectionController selection = new SelectionController(100L);
        SelectionInteractionController interactions = new SelectionInteractionController(selection);

        SelectionInteractionController.Result start = interactions.apply(
                SelectionInteractionController.Action.SET_START,
                Optional.of(new SelectionPoint(-2, 3, 4)));
        SelectionInteractionController.Result end = interactions.apply(
                SelectionInteractionController.Action.SET_END,
                Optional.of(new SelectionPoint(1, 5, 4)));

        assertEquals(SelectionInteractionController.Status.APPLIED, start.status());
        assertEquals(new SelectionPoint(-2, 3, 4), start.snapshot().selection().start().orElseThrow());
        assertEquals(SelectionInteractionController.Status.APPLIED, end.status());
        assertTrue(end.snapshot().validation().valid());
        assertEquals(12L, end.snapshot().validation().bounds().orElseThrow().blockCount().longValueExact());
    }

    @Test
    void missingTargetDoesNotMutateSelectionAndReturnsActionableMessage() {
        SelectionController selection = new SelectionController(100L);
        SelectionInteractionController interactions = new SelectionInteractionController(selection);

        SelectionInteractionController.Result result = interactions.apply(
                SelectionInteractionController.Action.SET_START,
                Optional.empty());

        assertEquals(SelectionInteractionController.Status.NO_TARGET, result.status());
        assertEquals(0L, result.snapshot().revision());
        assertTrue(result.snapshot().selection().start().isEmpty());
        assertFalse(result.message().isBlank());
    }

    @Test
    void clearActionsRemoveIndividualAndAllCorners() {
        SelectionController selection = new SelectionController(100L);
        SelectionInteractionController interactions = new SelectionInteractionController(selection);
        interactions.apply(
                SelectionInteractionController.Action.SET_START,
                Optional.of(new SelectionPoint(0, 0, 0)));
        interactions.apply(
                SelectionInteractionController.Action.SET_END,
                Optional.of(new SelectionPoint(1, 1, 1)));

        SelectionInteractionController.Result clearedStart = interactions.apply(
                SelectionInteractionController.Action.CLEAR_START,
                Optional.empty());
        assertTrue(clearedStart.snapshot().selection().start().isEmpty());
        assertTrue(clearedStart.snapshot().selection().end().isPresent());

        SelectionInteractionController.Result clearedAll = interactions.apply(
                SelectionInteractionController.Action.CLEAR_ALL,
                Optional.empty());
        assertTrue(clearedAll.snapshot().selection().start().isEmpty());
        assertTrue(clearedAll.snapshot().selection().end().isEmpty());
    }
}
