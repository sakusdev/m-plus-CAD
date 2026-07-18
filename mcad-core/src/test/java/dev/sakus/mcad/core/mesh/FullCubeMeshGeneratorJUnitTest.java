/*
 * SPDX-License-Identifier: MPL-2.0
 */
package dev.sakus.mcad.core.mesh;

import java.io.IOException;
import org.junit.jupiter.api.Test;

/** JUnit Platform adapter for the existing self-contained mesh contract scenarios. */
final class FullCubeMeshGeneratorJUnitTest {
    private final FullCubeMeshGeneratorTest delegate = new FullCubeMeshGeneratorTest();

    @Test
    void oneCubeHasOutwardWindingAndNormals() {
        delegate.oneCubeHasOutwardWindingAndNormals();
    }

    @Test
    void twoAdjacentSameBlocksRemoveBothSharedFaces() {
        delegate.twoAdjacentSameBlocksRemoveBothSharedFaces();
    }

    @Test
    void twoAdjacentDifferentBlocksRemainSeparateAndCullBoundary() {
        delegate.twoAdjacentDifferentBlocksRemainSeparateAndCullBoundary();
    }

    @Test
    void solidTwoByTwoByTwoHasOnlyExteriorFaces() {
        delegate.solidTwoByTwoByTwoHasOnlyExteriorFaces();
    }

    @Test
    void hollowThreeByThreeByThreeKeepsCavityFaces() {
        delegate.hollowThreeByThreeByThreeKeepsCavityFaces();
    }

    @Test
    void disconnectedIslandsShareStableBlockGroup() {
        delegate.disconnectedIslandsShareStableBlockGroup();
    }

    @Test
    void outputIsDeterministicAndMatchesFixture() throws IOException {
        delegate.outputIsDeterministicAndMatchesFixture();
    }

    @Test
    void cancellationStopsWithoutReturningPartialScene() {
        delegate.cancellationStopsWithoutReturningPartialScene();
    }

    @Test
    void hiddenFaceRemovalCanBeDisabled() {
        delegate.hiddenFaceRemovalCanBeDisabled();
    }

    @Test
    void negativeWorldOriginDoesNotAffectRelativeGeometry() {
        delegate.negativeWorldOriginDoesNotAffectRelativeGeometry();
    }

    @Test
    void configuredBlockLimitIsEnforced() {
        delegate.configuredBlockLimitIsEnforced();
    }
}
