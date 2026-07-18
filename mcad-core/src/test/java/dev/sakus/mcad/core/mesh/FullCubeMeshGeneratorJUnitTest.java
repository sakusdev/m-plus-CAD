/*
 * SPDX-License-Identifier: MPL-2.0
 */
package dev.sakus.mcad.core.mesh;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
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
        Path expectedLocation = Path.of("fixtures/mesh/full-cube-deterministic.txt");
        boolean copiedForModuleWorkingDirectory = !Files.exists(expectedLocation);
        if (copiedForModuleWorkingDirectory) {
            Path repositoryFixture = Path.of("..", "fixtures", "mesh", "full-cube-deterministic.txt");
            Files.createDirectories(expectedLocation.getParent());
            Files.copy(repositoryFixture, expectedLocation, StandardCopyOption.REPLACE_EXISTING);
        }
        try {
            delegate.outputIsDeterministicAndMatchesFixture();
        } finally {
            if (copiedForModuleWorkingDirectory) {
                Files.deleteIfExists(expectedLocation);
            }
        }
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
