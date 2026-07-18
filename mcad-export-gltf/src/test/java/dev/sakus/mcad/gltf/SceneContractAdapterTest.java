/*
 * SPDX-License-Identifier: MPL-2.0
 */
package dev.sakus.mcad.gltf;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.sakus.mcad.api.Quaterniond;
import dev.sakus.mcad.api.Transform;
import dev.sakus.mcad.api.Vec3d;

import java.util.List;

import org.junit.jupiter.api.Test;

final class SceneContractAdapterTest {
    @Test
    void defaultsMissingAdditiveAccessorsForLegacyContract() {
        Object legacy = new LegacyElement("legacy");
        assertEquals(Transform.IDENTITY, SceneContractAdapter.originTransform(legacy));
        assertEquals(Transform.IDENTITY, SceneContractAdapter.transform(legacy));
        assertEquals(List.of(), SceneContractAdapter.sourceReferences(legacy));
    }

    @Test
    void readsCurrentOriginTransformElementTransformAndSources() {
        Transform origin = new Transform(new Vec3d(-1.0, 0.0, 2.0), Quaterniond.IDENTITY, Vec3d.ONE);
        Transform local = new Transform(new Vec3d(3.0, 4.0, 5.0), Quaterniond.IDENTITY, Vec3d.ONE);
        CurrentScene scene = new CurrentScene(origin);
        CurrentElement element = new CurrentElement(local, List.of("source/a"));

        assertEquals(origin, SceneContractAdapter.originTransform(scene));
        assertEquals(local, SceneContractAdapter.transform(element));
        assertEquals(List.of("source/a"), SceneContractAdapter.sourceReferences(element));
    }

    private record LegacyElement(String stableId) {
    }

    private record CurrentScene(Transform originTransform) {
    }

    private record CurrentElement(Transform transform, List<String> sourceReferences) {
    }
}
