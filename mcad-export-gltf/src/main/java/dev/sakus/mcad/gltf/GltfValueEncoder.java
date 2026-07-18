/*
 * SPDX-License-Identifier: MPL-2.0
 */
package dev.sakus.mcad.gltf;

import dev.sakus.mcad.api.CanonicalIdentifier;
import dev.sakus.mcad.api.Color4d;
import dev.sakus.mcad.api.MetadataValue;
import dev.sakus.mcad.api.Quaterniond;
import dev.sakus.mcad.api.Transform;
import dev.sakus.mcad.api.Vec3d;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.RecordComponent;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.OptionalLong;
import java.util.TreeMap;

final class GltfValueEncoder {
    private GltfValueEncoder() {
    }

    static List<double[]> vectors(List<Vec3d> values) {
        List<double[]> output = new ArrayList<>(values.size());
        for (Vec3d value : values) {
            output.add(new double[]{value.x(), value.y(), value.z()});
        }
        return output;
    }

    static List<double[]> normalizedVectors(List<Vec3d> values) {
        List<double[]> output = new ArrayList<>(values.size());
        for (Vec3d value : values) {
            double magnitude = GltfPreflight.magnitude(value.x(), value.y(), value.z());
            output.add(new double[]{value.x() / magnitude, value.y() / magnitude, value.z() / magnitude});
        }
        return output;
    }

    static List<double[]> colours(List<Color4d> values) {
        List<double[]> output = new ArrayList<>(values.size());
        for (Color4d value : values) {
            output.add(new double[]{value.red(), value.green(), value.blue(), value.alpha()});
        }
        return output;
    }

    static List<Object> color(Color4d value) {
        return List.of(value.red(), value.green(), value.blue(), value.alpha());
    }

    static void putTransform(LinkedHashMap<String, Object> output, Transform value) {
        if (!value.translation().equals(Vec3d.ZERO)) {
            output.put("translation", List.of(
                    value.translation().x(), value.translation().y(), value.translation().z()));
        }
        Quaterniond rotation = normalized(value.rotation());
        if (!rotation.equals(Quaterniond.IDENTITY)) {
            output.put("rotation", List.of(rotation.x(), rotation.y(), rotation.z(), rotation.w()));
        }
        if (!value.scale().equals(Vec3d.ONE)) {
            output.put("scale", List.of(value.scale().x(), value.scale().y(), value.scale().z()));
        }
    }

    static Object transform(Transform value) {
        LinkedHashMap<String, Object> output = new LinkedHashMap<>();
        output.put("translation", List.of(
                value.translation().x(), value.translation().y(), value.translation().z()));
        Quaterniond rotation = normalized(value.rotation());
        output.put("rotation", List.of(rotation.x(), rotation.y(), rotation.z(), rotation.w()));
        output.put("scale", List.of(value.scale().x(), value.scale().y(), value.scale().z()));
        return output;
    }

    static LinkedHashMap<String, Object> attachmentExtras(String stableId, String meshId) {
        LinkedHashMap<String, Object> extras = new LinkedHashMap<>();
        extras.put("mcadStableId", stableId);
        extras.put("mcadAttachedMeshId", meshId);
        return extras;
    }

    static LinkedHashMap<String, Object> baseExtras(
            String stableId,
            Map<CanonicalIdentifier, MetadataValue> customProperties) {
        LinkedHashMap<String, Object> extras = new LinkedHashMap<>();
        extras.put("mcadStableId", stableId);
        if (!customProperties.isEmpty()) {
            extras.put("mcadCustomProperties", metadataMap(customProperties));
        }
        return extras;
    }

    static Object neutral(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof MetadataValue metadataValue) {
            return metadata(metadataValue);
        }
        if (value instanceof CanonicalIdentifier identifier) {
            return identifier.toString();
        }
        if (value instanceof String || value instanceof Boolean || value instanceof Number) {
            return value;
        }
        if (value instanceof Enum<?> enumeration) {
            return enumeration.name();
        }
        if (value instanceof Optional<?> optional) {
            return optional.map(GltfValueEncoder::neutral).orElse(null);
        }
        if (value instanceof OptionalDouble optional) {
            return optional.isPresent() ? optional.getAsDouble() : null;
        }
        if (value instanceof OptionalLong optional) {
            return optional.isPresent() ? optional.getAsLong() : null;
        }
        if (value instanceof OptionalInt optional) {
            return optional.isPresent() ? optional.getAsInt() : null;
        }
        if (value instanceof Collection<?> collection) {
            List<Object> result = new ArrayList<>(collection.size());
            for (Object element : collection) {
                result.add(neutral(element));
            }
            return List.copyOf(result);
        }
        if (value instanceof Map<?, ?> map) {
            TreeMap<String, Object> sorted = new TreeMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                sorted.put(String.valueOf(entry.getKey()), neutral(entry.getValue()));
            }
            return Collections.unmodifiableMap(sorted);
        }
        Class<?> type = value.getClass();
        if (type.isRecord()) {
            LinkedHashMap<String, Object> result = new LinkedHashMap<>();
            for (RecordComponent component : type.getRecordComponents()) {
                try {
                    result.put(component.getName(), neutral(component.getAccessor().invoke(value)));
                } catch (IllegalAccessException | InvocationTargetException exception) {
                    throw new IllegalStateException("cannot serialize neutral record " + type.getName(), exception);
                }
            }
            return result;
        }
        return value.toString();
    }

    private static Quaterniond normalized(Quaterniond value) {
        double magnitude = Math.sqrt(value.x() * value.x() + value.y() * value.y()
                + value.z() * value.z() + value.w() * value.w());
        return new Quaterniond(value.x() / magnitude, value.y() / magnitude,
                value.z() / magnitude, value.w() / magnitude);
    }

    private static Object metadata(MetadataValue value) {
        return switch (value) {
            case MetadataValue.StringValue stringValue -> stringValue.value();
            case MetadataValue.LongValue longValue -> longValue.value();
            case MetadataValue.DoubleValue doubleValue -> doubleValue.value();
            case MetadataValue.BooleanValue booleanValue -> booleanValue.value();
            case MetadataValue.ListValue listValue -> neutral(listValue.values());
            case MetadataValue.MapValue mapValue -> metadataMap(mapValue.values());
        };
    }

    private static Object metadataMap(Map<CanonicalIdentifier, MetadataValue> values) {
        LinkedHashMap<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<CanonicalIdentifier, MetadataValue> entry : values.entrySet()) {
            result.put(entry.getKey().toString(), metadata(entry.getValue()));
        }
        return result;
    }
}
