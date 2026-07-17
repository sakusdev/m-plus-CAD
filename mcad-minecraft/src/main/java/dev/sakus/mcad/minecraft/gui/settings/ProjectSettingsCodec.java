/*
 * SPDX-License-Identifier: MPL-2.0
 */
package dev.sakus.mcad.minecraft.gui.settings;

import dev.sakus.mcad.api.ApiVersions;
import dev.sakus.mcad.api.CanonicalIdentifier;
import dev.sakus.mcad.api.CollisionKind;
import dev.sakus.mcad.api.MetadataValue;
import dev.sakus.mcad.api.ProjectSettings;
import dev.sakus.mcad.api.SchemaVersion;
import dev.sakus.mcad.api.Vec3d;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/**
 * Deterministic versioned binary codec for {@link ProjectSettings}.
 */
public final class ProjectSettingsCodec {
    public static final int CURRENT_STORAGE_VERSION = 1;

    private static final int MAGIC = 0x4D434144; // MCAD
    private static final int LEGACY_STORAGE_VERSION = 0;
    private static final int MAX_DOCUMENT_BYTES = 16 * 1024 * 1024;
    private static final int MAX_STRING_BYTES = 1024 * 1024;
    private static final int MAX_COLLECTION_SIZE = 1_000_000;
    private static final int MAX_METADATA_DEPTH = 64;

    private static final int METADATA_STRING = 1;
    private static final int METADATA_LONG = 2;
    private static final int METADATA_DOUBLE = 3;
    private static final int METADATA_BOOLEAN = 4;
    private static final int METADATA_LIST = 5;
    private static final int METADATA_MAP = 6;

    public byte[] encode(ProjectSettings settings) {
        ProjectSettings checked = Objects.requireNonNull(settings, "settings");
        requireCurrentSchema(checked.schemaVersion(), "settings");
        return encodeStorageVersion(checked, CURRENT_STORAGE_VERSION);
    }

    public DecodedProjectSettings decode(byte[] document, ProjectSettings migrationDefaults) {
        Objects.requireNonNull(document, "document");
        ProjectSettings defaults = Objects.requireNonNull(migrationDefaults, "migrationDefaults");
        requireCurrentSchema(defaults.schemaVersion(), "migrationDefaults");
        if (document.length > MAX_DOCUMENT_BYTES) {
            throw new IllegalArgumentException("settings document exceeds " + MAX_DOCUMENT_BYTES + " bytes");
        }
        try (var bytes = new ByteArrayInputStream(document);
                var input = new DataInputStream(bytes)) {
            int magic = input.readInt();
            if (magic != MAGIC) {
                throw new IllegalArgumentException("not an m+CAD settings document");
            }
            int storageVersion = input.readInt();
            if (storageVersion != LEGACY_STORAGE_VERSION && storageVersion != CURRENT_STORAGE_VERSION) {
                throw new IllegalArgumentException("unsupported settings storage version: " + storageVersion);
            }
            ProjectSettings settings = readSettings(input, storageVersion, defaults);
            if (bytes.available() != 0) {
                throw new IllegalArgumentException("settings document contains trailing data");
            }
            return new DecodedProjectSettings(
                    settings,
                    storageVersion,
                    storageVersion != CURRENT_STORAGE_VERSION);
        } catch (EOFException exception) {
            throw new IllegalArgumentException("settings document is truncated", exception);
        } catch (IOException exception) {
            throw new UncheckedIOException("failed to decode settings", exception);
        }
    }

    static byte[] encodeLegacyVersionZero(ProjectSettings settings) {
        ProjectSettings checked = Objects.requireNonNull(settings, "settings");
        requireLegacyCompatibleSchema(checked.schemaVersion());
        return encodeStorageVersion(checked, LEGACY_STORAGE_VERSION);
    }

    private static byte[] encodeStorageVersion(ProjectSettings settings, int storageVersion) {
        try (var bytes = new ByteArrayOutputStream();
                var output = new DataOutputStream(bytes)) {
            output.writeInt(MAGIC);
            output.writeInt(storageVersion);
            writeSettings(output, settings, storageVersion);
            output.flush();
            byte[] encoded = bytes.toByteArray();
            if (encoded.length > MAX_DOCUMENT_BYTES) {
                throw new IllegalArgumentException("settings document exceeds " + MAX_DOCUMENT_BYTES + " bytes");
            }
            return encoded;
        } catch (IOException exception) {
            throw new UncheckedIOException("failed to encode settings", exception);
        }
    }

    private static void writeSettings(DataOutputStream output, ProjectSettings settings, int storageVersion)
            throws IOException {
        writeSchemaVersion(output, settings.schemaVersion());

        output.writeLong(settings.selection().maximumBlockCount());
        output.writeBoolean(settings.selection().preserveEmptyCells());

        output.writeBoolean(settings.geometry().hiddenFaceRemoval());
        output.writeBoolean(settings.geometry().rejectDegenerateTriangles());

        writeCount(output, settings.meshSeparation().orderedKeys().size());
        for (ProjectSettings.SeparationKey key : settings.meshSeparation().orderedKeys()) {
            writeEnum(output, key);
        }

        writeEnum(output, settings.materials().mode());
        output.writeBoolean(settings.materials().userMappingId().isPresent());
        if (settings.materials().userMappingId().isPresent()) {
            writeString(output, settings.materials().userMappingId().orElseThrow());
        }

        writeEnum(output, settings.transform().originMode());
        writeVec3d(output, settings.transform().explicitOriginOffset());
        writeVec3d(output, settings.transform().rotationDegrees());
        output.writeDouble(settings.transform().unitScale());
        writeEnum(output, settings.transform().targetAxis());

        output.writeBoolean(settings.markers().enabled());
        output.writeBoolean(settings.markers().consumeSemanticSources());
        output.writeBoolean(settings.markers().previewInterpretation());

        output.writeBoolean(settings.optimization().greedyMeshing());
        output.writeBoolean(settings.optimization().instancing());
        output.writeBoolean(settings.optimization().preserveMaterialBoundaries());

        if (storageVersion >= CURRENT_STORAGE_VERSION) {
            output.writeBoolean(settings.animation().enabled());
            output.writeInt(settings.animation().framesPerSecond());
        }

        output.writeBoolean(settings.collision().enabled());
        writeEnum(output, settings.collision().defaultKind());

        writeIdentifier(output, settings.output().exporterId());
        writeString(output, settings.output().destination());
        writeEnum(output, settings.output().lossPolicy());
        writeCount(output, settings.output().exporterOptions().size());
        for (var entry : settings.output().exporterOptions().entrySet()) {
            writeIdentifier(output, entry.getKey());
            writeMetadata(output, entry.getValue(), 0);
        }

        if (storageVersion >= CURRENT_STORAGE_VERSION) {
            output.writeBoolean(settings.preview().selectionOutline());
            output.writeBoolean(settings.preview().diagnosticsOverlay());
            output.writeBoolean(settings.preview().consumedMarkers());
        }

        output.writeInt(settings.advanced().workerThreads());
        output.writeLong(settings.advanced().memoryLimitBytes());
    }

    private static ProjectSettings readSettings(
            DataInputStream input,
            int storageVersion,
            ProjectSettings migrationDefaults) throws IOException {
        SchemaVersion sourceSchemaVersion = readSchemaVersion(input);
        SchemaVersion schemaVersion;
        if (storageVersion == CURRENT_STORAGE_VERSION) {
            requireCurrentSchema(sourceSchemaVersion, "settings document");
            schemaVersion = sourceSchemaVersion;
        } else {
            requireLegacyCompatibleSchema(sourceSchemaVersion);
            schemaVersion = ApiVersions.PROJECT_SETTINGS;
        }

        var selection = new ProjectSettings.SelectionSettings(input.readLong(), input.readBoolean());
        var geometry = new ProjectSettings.GeometrySettings(input.readBoolean(), input.readBoolean());

        int keyCount = readCount(input, "mesh separation key count");
        List<ProjectSettings.SeparationKey> keys = new ArrayList<>(keyCount);
        for (int index = 0; index < keyCount; index++) {
            keys.add(readEnum(input, ProjectSettings.SeparationKey.class, "mesh separation key"));
        }
        var meshSeparation = new ProjectSettings.MeshSeparationSettings(keys);

        ProjectSettings.MaterialMode materialMode =
                readEnum(input, ProjectSettings.MaterialMode.class, "material mode");
        var mappingId = input.readBoolean()
                ? java.util.Optional.of(readString(input, "user mapping id"))
                : java.util.Optional.<String>empty();
        var materials = new ProjectSettings.MaterialSettings(materialMode, mappingId);

        var transform = new ProjectSettings.TransformSettings(
                readEnum(input, ProjectSettings.OriginMode.class, "origin mode"),
                readVec3d(input),
                readVec3d(input),
                input.readDouble(),
                readEnum(input, ProjectSettings.AxisConvention.class, "axis convention"));

        var markers = new ProjectSettings.MarkerSettings(
                input.readBoolean(), input.readBoolean(), input.readBoolean());
        var optimization = new ProjectSettings.OptimizationSettings(
                input.readBoolean(), input.readBoolean(), input.readBoolean());

        ProjectSettings.AnimationSettings animation = storageVersion >= CURRENT_STORAGE_VERSION
                ? new ProjectSettings.AnimationSettings(input.readBoolean(), input.readInt())
                : migrationDefaults.animation();

        var collision = new ProjectSettings.CollisionSettings(
                input.readBoolean(),
                readEnum(input, CollisionKind.class, "collision kind"));

        CanonicalIdentifier exporterId = readIdentifier(input);
        String destination = readString(input, "output destination");
        ProjectSettings.LossPolicy lossPolicy =
                readEnum(input, ProjectSettings.LossPolicy.class, "loss policy");
        int optionCount = readCount(input, "exporter option count");
        Map<CanonicalIdentifier, MetadataValue> options = new TreeMap<>();
        for (int index = 0; index < optionCount; index++) {
            CanonicalIdentifier key = readIdentifier(input);
            MetadataValue previous = options.put(key, readMetadata(input, 0));
            if (previous != null) {
                throw new IllegalArgumentException("duplicate exporter option: " + key);
            }
        }
        var output = new ProjectSettings.OutputSettings(exporterId, destination, lossPolicy, options);

        ProjectSettings.PreviewSettings preview = storageVersion >= CURRENT_STORAGE_VERSION
                ? new ProjectSettings.PreviewSettings(
                        input.readBoolean(), input.readBoolean(), input.readBoolean())
                : migrationDefaults.preview();

        var advanced = new ProjectSettings.AdvancedSettings(input.readInt(), input.readLong());

        return new ProjectSettings(
                schemaVersion,
                selection,
                geometry,
                meshSeparation,
                materials,
                transform,
                markers,
                optimization,
                animation,
                collision,
                output,
                preview,
                advanced);
    }

    private static void writeMetadata(DataOutputStream output, MetadataValue value, int depth) throws IOException {
        Objects.requireNonNull(value, "metadata value");
        requireMetadataDepth(depth);
        switch (value) {
            case MetadataValue.StringValue stringValue -> {
                output.writeByte(METADATA_STRING);
                writeString(output, stringValue.value());
            }
            case MetadataValue.LongValue longValue -> {
                output.writeByte(METADATA_LONG);
                output.writeLong(longValue.value());
            }
            case MetadataValue.DoubleValue doubleValue -> {
                output.writeByte(METADATA_DOUBLE);
                output.writeDouble(doubleValue.value());
            }
            case MetadataValue.BooleanValue booleanValue -> {
                output.writeByte(METADATA_BOOLEAN);
                output.writeBoolean(booleanValue.value());
            }
            case MetadataValue.ListValue listValue -> {
                output.writeByte(METADATA_LIST);
                writeCount(output, listValue.values().size());
                for (MetadataValue child : listValue.values()) {
                    writeMetadata(output, child, depth + 1);
                }
            }
            case MetadataValue.MapValue mapValue -> {
                output.writeByte(METADATA_MAP);
                writeCount(output, mapValue.values().size());
                for (var entry : mapValue.values().entrySet()) {
                    writeIdentifier(output, entry.getKey());
                    writeMetadata(output, entry.getValue(), depth + 1);
                }
            }
        }
    }

    private static MetadataValue readMetadata(DataInputStream input, int depth) throws IOException {
        requireMetadataDepth(depth);
        int type = input.readUnsignedByte();
        return switch (type) {
            case METADATA_STRING -> new MetadataValue.StringValue(readString(input, "metadata string"));
            case METADATA_LONG -> new MetadataValue.LongValue(input.readLong());
            case METADATA_DOUBLE -> new MetadataValue.DoubleValue(input.readDouble());
            case METADATA_BOOLEAN -> new MetadataValue.BooleanValue(input.readBoolean());
            case METADATA_LIST -> {
                int size = readCount(input, "metadata list size");
                List<MetadataValue> values = new ArrayList<>(size);
                for (int index = 0; index < size; index++) {
                    values.add(readMetadata(input, depth + 1));
                }
                yield new MetadataValue.ListValue(values);
            }
            case METADATA_MAP -> {
                int size = readCount(input, "metadata map size");
                Map<CanonicalIdentifier, MetadataValue> values = new TreeMap<>();
                for (int index = 0; index < size; index++) {
                    CanonicalIdentifier key = readIdentifier(input);
                    MetadataValue previous = values.put(key, readMetadata(input, depth + 1));
                    if (previous != null) {
                        throw new IllegalArgumentException("duplicate metadata key: " + key);
                    }
                }
                yield new MetadataValue.MapValue(values);
            }
            default -> throw new IllegalArgumentException("unknown metadata value type: " + type);
        };
    }

    private static void writeSchemaVersion(DataOutputStream output, SchemaVersion version) throws IOException {
        output.writeInt(version.major());
        output.writeInt(version.minor());
    }

    private static SchemaVersion readSchemaVersion(DataInputStream input) throws IOException {
        return new SchemaVersion(input.readInt(), input.readInt());
    }

    private static void writeIdentifier(DataOutputStream output, CanonicalIdentifier identifier) throws IOException {
        writeString(output, identifier.namespace());
        writeString(output, identifier.path());
    }

    private static CanonicalIdentifier readIdentifier(DataInputStream input) throws IOException {
        return new CanonicalIdentifier(
                readString(input, "identifier namespace"),
                readString(input, "identifier path"));
    }

    private static void writeVec3d(DataOutputStream output, Vec3d value) throws IOException {
        output.writeDouble(value.x());
        output.writeDouble(value.y());
        output.writeDouble(value.z());
    }

    private static Vec3d readVec3d(DataInputStream input) throws IOException {
        return new Vec3d(input.readDouble(), input.readDouble(), input.readDouble());
    }

    private static void writeString(DataOutputStream output, String value) throws IOException {
        byte[] encoded = encodeUtf8(Objects.requireNonNull(value, "value"));
        if (encoded.length > MAX_STRING_BYTES) {
            throw new IllegalArgumentException("string exceeds " + MAX_STRING_BYTES + " UTF-8 bytes");
        }
        output.writeInt(encoded.length);
        output.write(encoded);
    }

    private static String readString(DataInputStream input, String name) throws IOException {
        int size = input.readInt();
        if (size < 0 || size > MAX_STRING_BYTES) {
            throw new IllegalArgumentException(name + " byte length is invalid: " + size);
        }
        byte[] encoded = input.readNBytes(size);
        if (encoded.length != size) {
            throw new EOFException(name + " is truncated");
        }
        try {
            return StandardCharsets.UTF_8
                    .newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(encoded))
                    .toString();
        } catch (CharacterCodingException exception) {
            throw new IllegalArgumentException(name + " is not valid UTF-8", exception);
        }
    }

    private static byte[] encodeUtf8(String value) {
        try {
            ByteBuffer encoded = StandardCharsets.UTF_8
                    .newEncoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .encode(CharBuffer.wrap(value));
            byte[] bytes = new byte[encoded.remaining()];
            encoded.get(bytes);
            return bytes;
        } catch (CharacterCodingException exception) {
            throw new IllegalArgumentException("string contains invalid Unicode", exception);
        }
    }

    private static void writeCount(DataOutputStream output, int size) throws IOException {
        if (size < 0 || size > MAX_COLLECTION_SIZE) {
            throw new IllegalArgumentException("collection size is invalid: " + size);
        }
        output.writeInt(size);
    }

    private static int readCount(DataInputStream input, String name) throws IOException {
        int size = input.readInt();
        if (size < 0 || size > MAX_COLLECTION_SIZE) {
            throw new IllegalArgumentException(name + " is invalid: " + size);
        }
        return size;
    }

    private static <E extends Enum<E>> void writeEnum(DataOutputStream output, E value) throws IOException {
        writeString(output, Objects.requireNonNull(value, "enum value").name());
    }

    private static <E extends Enum<E>> E readEnum(DataInputStream input, Class<E> type, String name)
            throws IOException {
        String value = readString(input, name);
        try {
            return Enum.valueOf(type, value);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("unknown " + name + ": " + value, exception);
        }
    }

    private static void requireCurrentSchema(SchemaVersion version, String name) {
        Objects.requireNonNull(version, name + " schemaVersion");
        if (!version.equals(ApiVersions.PROJECT_SETTINGS)) {
            throw new IllegalArgumentException(
                    name + " schema version " + version
                            + " is unsupported; expected " + ApiVersions.PROJECT_SETTINGS);
        }
    }

    private static void requireLegacyCompatibleSchema(SchemaVersion version) {
        Objects.requireNonNull(version, "schemaVersion");
        SchemaVersion current = ApiVersions.PROJECT_SETTINGS;
        if (version.major() != current.major() || version.compareTo(current) > 0) {
            throw new IllegalArgumentException(
                    "legacy settings schema version " + version
                            + " cannot migrate to " + current);
        }
    }

    private static void requireMetadataDepth(int depth) {
        if (depth < 0 || depth > MAX_METADATA_DEPTH) {
            throw new IllegalArgumentException("metadata nesting exceeds " + MAX_METADATA_DEPTH);
        }
    }
}
