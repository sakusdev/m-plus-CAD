/*
 * SPDX-License-Identifier: MPL-2.0
 */
package dev.sakus.mcad.minecraft.snapshot;

import dev.sakus.mcad.api.BlockEntry;
import dev.sakus.mcad.api.BlockPosition;
import dev.sakus.mcad.api.CanonicalIdentifier;
import dev.sakus.mcad.api.IntSize3;
import dev.sakus.mcad.api.MetadataValue;
import dev.sakus.mcad.api.SchemaVersion;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.DigestOutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.Map;
import java.util.Optional;

final class SnapshotContentHasher {
    private SnapshotContentHasher() {
    }

    static String hash(
            SchemaVersion schemaVersion,
            IntSize3 size,
            Optional<BlockPosition> sourceWorldOrigin,
            List<BlockEntry> blocks,
            Map<CanonicalIdentifier, MetadataValue> metadata) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (var output = new DataOutputStream(new DigestOutputStream(NullOutputStream.INSTANCE, digest))) {
                output.writeInt(schemaVersion.major());
                output.writeInt(schemaVersion.minor());
                output.writeInt(size.width());
                output.writeInt(size.height());
                output.writeInt(size.depth());
                output.writeBoolean(sourceWorldOrigin.isPresent());
                if (sourceWorldOrigin.isPresent()) {
                    writePosition(output, sourceWorldOrigin.orElseThrow());
                }
                output.writeInt(blocks.size());
                for (BlockEntry block : blocks) {
                    writePosition(output, block.relativePosition());
                    writeString(output, block.blockId().toString());
                    output.writeInt(block.stateProperties().size());
                    for (var property : block.stateProperties().entrySet()) {
                        writeString(output, property.getKey());
                        writeString(output, property.getValue());
                    }
                    writeMetadataMap(output, block.metadata());
                }
                writeMetadataMap(output, metadata);
            }
            return "sha256:" + toHex(digest.digest());
        } catch (IOException exception) {
            throw new IllegalStateException("failed to hash snapshot content", exception);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static void writePosition(DataOutputStream output, BlockPosition position) throws IOException {
        output.writeInt(position.x());
        output.writeInt(position.y());
        output.writeInt(position.z());
    }

    private static void writeMetadataMap(
            DataOutputStream output, Map<CanonicalIdentifier, MetadataValue> metadata) throws IOException {
        output.writeInt(metadata.size());
        for (var entry : metadata.entrySet()) {
            writeString(output, entry.getKey().toString());
            writeMetadata(output, entry.getValue());
        }
    }

    private static void writeMetadata(DataOutputStream output, MetadataValue value) throws IOException {
        switch (value) {
            case MetadataValue.StringValue stringValue -> {
                output.writeByte(1);
                writeString(output, stringValue.value());
            }
            case MetadataValue.LongValue longValue -> {
                output.writeByte(2);
                output.writeLong(longValue.value());
            }
            case MetadataValue.DoubleValue doubleValue -> {
                output.writeByte(3);
                output.writeLong(Double.doubleToLongBits(doubleValue.value()));
            }
            case MetadataValue.BooleanValue booleanValue -> {
                output.writeByte(4);
                output.writeBoolean(booleanValue.value());
            }
            case MetadataValue.ListValue listValue -> {
                output.writeByte(5);
                output.writeInt(listValue.values().size());
                for (MetadataValue child : listValue.values()) {
                    writeMetadata(output, child);
                }
            }
            case MetadataValue.MapValue mapValue -> {
                output.writeByte(6);
                writeMetadataMap(output, mapValue.values());
            }
        }
    }

    private static void writeString(DataOutputStream output, String value) throws IOException {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        output.writeInt(bytes.length);
        output.write(bytes);
    }

    private static String toHex(byte[] bytes) {
        var builder = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) {
            builder.append(Character.forDigit((value >>> 4) & 0x0f, 16));
            builder.append(Character.forDigit(value & 0x0f, 16));
        }
        return builder.toString();
    }

    private static final class NullOutputStream extends java.io.OutputStream {
        private static final NullOutputStream INSTANCE = new NullOutputStream();

        private NullOutputStream() {
        }

        @Override
        public void write(int value) {
            // DigestOutputStream consumes the bytes; no backing buffer is required.
        }

        @Override
        public void write(byte[] bytes, int offset, int length) {
            // DigestOutputStream consumes the bytes; no backing buffer is required.
        }
    }
}
