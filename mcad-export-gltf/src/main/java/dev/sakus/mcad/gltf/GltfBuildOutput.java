/*
 * SPDX-License-Identifier: MPL-2.0
 */
package dev.sakus.mcad.gltf;

record GltfBuildOutput(byte[] jsonBytes, byte[] binaryBytes, byte[] glbBytes) {
    GltfBuildOutput {
        jsonBytes = jsonBytes.clone();
        binaryBytes = binaryBytes.clone();
        glbBytes = glbBytes.clone();
    }

    @Override
    public byte[] jsonBytes() {
        return jsonBytes.clone();
    }

    @Override
    public byte[] binaryBytes() {
        return binaryBytes.clone();
    }

    @Override
    public byte[] glbBytes() {
        return glbBytes.clone();
    }
}
