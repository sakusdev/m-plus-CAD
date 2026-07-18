/*
 * SPDX-License-Identifier: MPL-2.0
 */
package dev.sakus.mcad.minecraft.selection;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SelectionOverlayModelTest {
    @Test
    void incompleteSelectionShowsPlaceholdersWithoutWireframe() {
        SelectionController controller = new SelectionController(100L);
        controller.setCorner(TwoPointSelection.Corner.START, new SelectionPoint(-1, 2, 3));

        SelectionOverlayModel overlay = SelectionOverlayModel.from(controller.snapshot());

        assertTrue(overlay.wireframe().isEmpty());
        assertEquals("— × — × —", overlay.dimensionsLabel());
        assertEquals("—", overlay.blockCountLabel());
        assertFalse(overlay.valid());
    }

    @Test
    void completeSelectionShowsDimensionsCountAndWireframe() {
        SelectionController controller = new SelectionController(100L);
        controller.setCorner(TwoPointSelection.Corner.START, new SelectionPoint(-1, 2, 3));
        controller.setCorner(TwoPointSelection.Corner.END, new SelectionPoint(1, 4, 3));

        SelectionOverlayModel overlay = SelectionOverlayModel.from(controller.snapshot());

        assertEquals("3 × 3 × 1", overlay.dimensionsLabel());
        assertEquals("9", overlay.blockCountLabel());
        assertEquals(12, overlay.wireframe().orElseThrow().segments().size());
        assertTrue(overlay.valid());
    }

    @Test
    void oversizedSelectionStillExposesBoundsWithActionableError() {
        SelectionController controller = new SelectionController(1L);
        controller.setCorner(TwoPointSelection.Corner.START, new SelectionPoint(0, 0, 0));
        controller.setCorner(TwoPointSelection.Corner.END, new SelectionPoint(1, 0, 0));

        SelectionOverlayModel overlay = SelectionOverlayModel.from(controller.snapshot());

        assertEquals(SelectionValidation.Code.TOO_LARGE, overlay.validationCode());
        assertTrue(overlay.wireframe().isPresent());
        assertFalse(overlay.message().isBlank());
        assertFalse(overlay.suggestedAction().isBlank());
    }
}
