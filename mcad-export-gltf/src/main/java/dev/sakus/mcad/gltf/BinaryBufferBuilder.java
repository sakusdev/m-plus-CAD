/*
 * SPDX-License-Identifier: MPL-2.0
 */
package dev.sakus.mcad.gltf;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

final class BinaryBufferBuilder {
    static final int ARRAY_BUFFER = 34962;
    static final int ELEMENT_ARRAY_BUFFER = 34963;

    private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    private final List<Object> bufferViews = new ArrayList<>();
    private final List<Object> accessors = new ArrayList<>();

    int addFloatVec3(List<double[]> values, boolean bounds, int target) {
        align(4);
        int offset = bytes.size();
        double[] minimum = {Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY};
        double[] maximum = {Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY};
        for (double[] value : values) {
            if (value.length != 3) {
                throw new IllegalArgumentException("VEC3 value must have three components");
            }
            for (int component = 0; component < 3; component++) {
                double number = value[component];
                if (!Double.isFinite(number)) {
                    throw new IllegalArgumentException("attribute value must be finite");
                }
                writeFloat((float) number);
                minimum[component] = Math.min(minimum[component], number);
                maximum[component] = Math.max(maximum[component], number);
            }
        }
        int view = addBufferView(offset, bytes.size() - offset, target);
        LinkedHashMap<String, Object> accessor = accessor(view, 5126, values.size(), "VEC3");
        if (bounds) {
            accessor.put("min", numbers(minimum));
            accessor.put("max", numbers(maximum));
        }
        return addAccessor(accessor);
    }

    int addFloatVec4(List<double[]> values, int target) {
        align(4);
        int offset = bytes.size();
        for (double[] value : values) {
            if (value.length != 4) {
                throw new IllegalArgumentException("VEC4 value must have four components");
            }
            for (double number : value) {
                if (!Double.isFinite(number)) {
                    throw new IllegalArgumentException("attribute value must be finite");
                }
                writeFloat((float) number);
            }
        }
        int view = addBufferView(offset, bytes.size() - offset, target);
        return addAccessor(accessor(view, 5126, values.size(), "VEC4"));
    }

    int addFloatScalar(List<Double> values, int target) {
        align(4);
        int offset = bytes.size();
        for (double value : values) {
            if (!Double.isFinite(value)) {
                throw new IllegalArgumentException("attribute value must be finite");
            }
            writeFloat((float) value);
        }
        int view = addBufferView(offset, bytes.size() - offset, target);
        return addAccessor(accessor(view, 5126, values.size(), "SCALAR"));
    }

    int addIndices(List<Integer> values) {
        int maximum = 0;
        int minimum = Integer.MAX_VALUE;
        for (int value : values) {
            if (value < 0) {
                throw new IllegalArgumentException("index must be non-negative");
            }
            maximum = Math.max(maximum, value);
            minimum = Math.min(minimum, value);
        }
        int componentType = maximum <= 0xffff ? 5123 : 5125;
        int alignment = componentType == 5123 ? 2 : 4;
        align(alignment);
        int offset = bytes.size();
        for (int value : values) {
            if (componentType == 5123) {
                writeShort(value);
            } else {
                writeInt(value);
            }
        }
        int view = addBufferView(offset, bytes.size() - offset, ELEMENT_ARRAY_BUFFER);
        LinkedHashMap<String, Object> accessor = accessor(view, componentType, values.size(), "SCALAR");
        accessor.put("min", List.of(minimum));
        accessor.put("max", List.of(maximum));
        return addAccessor(accessor);
    }

    byte[] bytes() {
        align(4);
        return bytes.toByteArray();
    }

    List<Object> bufferViews() {
        return List.copyOf(bufferViews);
    }

    List<Object> accessors() {
        return List.copyOf(accessors);
    }

    private int addBufferView(int offset, int length, int target) {
        LinkedHashMap<String, Object> view = new LinkedHashMap<>();
        view.put("buffer", 0);
        if (offset != 0) {
            view.put("byteOffset", offset);
        }
        view.put("byteLength", length);
        view.put("target", target);
        bufferViews.add(view);
        return bufferViews.size() - 1;
    }

    private int addAccessor(LinkedHashMap<String, Object> accessor) {
        accessors.add(accessor);
        return accessors.size() - 1;
    }

    private static LinkedHashMap<String, Object> accessor(int view, int componentType, int count, String type) {
        LinkedHashMap<String, Object> accessor = new LinkedHashMap<>();
        accessor.put("bufferView", view);
        accessor.put("componentType", componentType);
        accessor.put("count", count);
        accessor.put("type", type);
        return accessor;
    }

    private static List<Object> numbers(double[] values) {
        List<Object> result = new ArrayList<>(values.length);
        for (double value : values) {
            float serialized = (float) value;
            if (!Float.isFinite(serialized)) {
                throw new IllegalArgumentException("attribute value is outside finite float range");
            }
            result.add(serialized);
        }
        return List.copyOf(result);
    }

    private void align(int alignment) {
        while (bytes.size() % alignment != 0) {
            bytes.write(0);
        }
    }

    private void writeFloat(float value) {
        if (!Float.isFinite(value)) {
            throw new IllegalArgumentException("binary float value must be finite");
        }
        write(ByteBuffer.allocate(Float.BYTES).order(ByteOrder.LITTLE_ENDIAN).putFloat(value).array());
    }

    private void writeShort(int value) {
        write(ByteBuffer.allocate(Short.BYTES).order(ByteOrder.LITTLE_ENDIAN).putShort((short) value).array());
    }

    private void writeInt(int value) {
        write(ByteBuffer.allocate(Integer.BYTES).order(ByteOrder.LITTLE_ENDIAN).putInt(value).array());
    }

    private void write(byte[] value) {
        bytes.writeBytes(value);
    }
}
