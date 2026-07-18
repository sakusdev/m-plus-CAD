/*
 * SPDX-License-Identifier: MPL-2.0
 */
package dev.sakus.mcad.gltf;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

final class GlbWriter {
    private GlbWriter() {
    }

    static byte[] write(byte[] json, byte[] binary) {
        byte[] paddedJson = pad(json, 4, (byte) 0x20);
        byte[] paddedBinary = pad(binary, 4, (byte) 0x00);
        int binaryChunkBytes = paddedBinary.length == 0 ? 0 : 8 + paddedBinary.length;
        long totalLength = 12L + 8L + paddedJson.length + binaryChunkBytes;
        if (totalLength > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("GLB exceeds the supported in-memory size limit");
        }
        ByteBuffer output = ByteBuffer.allocate((int) totalLength).order(ByteOrder.LITTLE_ENDIAN);
        output.putInt(0x46546c67);
        output.putInt(2);
        output.putInt((int) totalLength);
        output.putInt(paddedJson.length);
        output.putInt(0x4e4f534a);
        output.put(paddedJson);
        if (paddedBinary.length > 0) {
            output.putInt(paddedBinary.length);
            output.putInt(0x004e4942);
            output.put(paddedBinary);
        }
        return output.array();
    }

    private static byte[] pad(byte[] input, int alignment, byte padding) {
        int paddedLength = (input.length + alignment - 1) / alignment * alignment;
        byte[] result = new byte[paddedLength];
        System.arraycopy(input, 0, result, 0, input.length);
        for (int index = input.length; index < paddedLength; index++) {
            result[index] = padding;
        }
        return result;
    }
}
