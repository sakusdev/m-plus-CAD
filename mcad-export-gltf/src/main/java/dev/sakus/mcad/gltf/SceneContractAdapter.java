/*
 * SPDX-License-Identifier: MPL-2.0
 */
package dev.sakus.mcad.gltf;

import dev.sakus.mcad.api.Transform;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Optional;

/**
 * Isolates additive GeneratedScene contract evolution inside the exporter module.
 *
 * <p>The integration branch may temporarily expose the pre-origin-transform API while
 * the next contract revision is being integrated. Missing additive accessors are mapped
 * to their neutral defaults, while the same exporter binary automatically consumes the
 * newer accessors once they are present.</p>
 */
final class SceneContractAdapter {
    private SceneContractAdapter() {
    }

    static Transform originTransform(Object scene) {
        return typedAccessor(scene, "originTransform", Transform.class).orElse(Transform.IDENTITY);
    }

    static Transform transform(Object sceneElement) {
        return typedAccessor(sceneElement, "transform", Transform.class).orElse(Transform.IDENTITY);
    }

    static List<?> sourceReferences(Object sceneElement) {
        Optional<Object> value = accessor(sceneElement, "sourceReferences");
        if (value.isEmpty()) {
            return List.of();
        }
        if (!(value.orElseThrow() instanceof List<?> list)) {
            throw new IllegalStateException("Contract accessor 'sourceReferences' did not return a List");
        }
        return List.copyOf(list);
    }

    private static <T> Optional<T> typedAccessor(Object target, String name, Class<T> resultType) {
        Optional<Object> value = accessor(target, name);
        if (value.isEmpty()) {
            return Optional.empty();
        }
        Object result = value.orElseThrow();
        if (!resultType.isInstance(result)) {
            throw new IllegalStateException("Contract accessor '" + name + "' returned "
                    + result.getClass().getName() + " instead of " + resultType.getName());
        }
        return Optional.of(resultType.cast(result));
    }

    private static Optional<Object> accessor(Object target, String name) {
        if (target == null) {
            throw new NullPointerException("target");
        }
        try {
            Method method = target.getClass().getMethod(name);
            Object value = method.invoke(target);
            if (value == null) {
                throw new IllegalStateException("Contract accessor '" + name + "' returned null");
            }
            return Optional.of(value);
        } catch (NoSuchMethodException exception) {
            return Optional.empty();
        } catch (IllegalAccessException | InvocationTargetException exception) {
            throw new IllegalStateException("Cannot read contract accessor '" + name + "' from "
                    + target.getClass().getName(), exception);
        }
    }
}
