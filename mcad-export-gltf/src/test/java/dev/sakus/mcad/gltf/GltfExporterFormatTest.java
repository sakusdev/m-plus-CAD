/*
 * SPDX-License-Identifier: MPL-2.0
 */
package dev.sakus.mcad.gltf;

import static dev.sakus.mcad.gltf.GltfTestFixtures.glbJson;
import static dev.sakus.mcad.gltf.GltfTestFixtures.material;
import static dev.sakus.mcad.gltf.GltfTestFixtures.mesh;
import static dev.sakus.mcad.gltf.GltfTestFixtures.numbers;
import static dev.sakus.mcad.gltf.GltfTestFixtures.occurrences;
import static dev.sakus.mcad.gltf.GltfTestFixtures.read;
import static dev.sakus.mcad.gltf.GltfTestFixtures.scene;
import static dev.sakus.mcad.gltf.GltfTestFixtures.sceneWithNodes;
import static dev.sakus.mcad.gltf.GltfTestFixtures.sha256;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.sakus.mcad.api.CancellationToken;
import dev.sakus.mcad.api.CanonicalIdentifier;
import dev.sakus.mcad.api.ExportOptions;
import dev.sakus.mcad.api.ExportResult;
import dev.sakus.mcad.api.ExportStatus;
import dev.sakus.mcad.api.GeneratedScene;
import dev.sakus.mcad.api.MaterialDefinition;
import dev.sakus.mcad.api.MeshGroup;
import dev.sakus.mcad.api.ProgressReporter;
import dev.sakus.mcad.api.SceneNode;
import dev.sakus.mcad.api.Transform;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class GltfExporterFormatTest {
    private static final Pattern BYTE_OFFSET = Pattern.compile("\\\"byteOffset\\\":(\\d+)");
    private final GltfExporter exporter = new GltfExporter();

    @TempDir
    Path temporaryDirectory;

    @Test
    void exportsBasicGltfWithAccessorsBoundsAndProducedFiles() throws Exception {
        GeneratedScene scene = scene(List.of(mesh("mesh/triangle", Optional.empty(), false)), List.of(), List.of());
        Path destination = temporaryDirectory.resolve("basic-triangle.gltf");

        ExportResult result = exporter.export(
                scene, destination, ExportOptions.EMPTY, ProgressReporter.NONE, CancellationToken.NONE);

        assertEquals(ExportStatus.SUCCESS, result.status());
        assertEquals(List.of("basic-triangle.bin", "basic-triangle.gltf"),
                result.producedFiles().stream().map(file -> file.relativePath()).toList());
        String json = Files.readString(destination, StandardCharsets.UTF_8);
        assertTrue(json.contains("\"asset\":{\"version\":\"2.0\""));
        assertTrue(json.contains("\"type\":\"VEC3\""));
        assertTrue(json.contains("\"min\":[0.0,0.0,0.0]"));
        assertTrue(json.contains("\"max\":[1.0,1.0,0.0]"));
        assertTrue(json.contains("\"uri\":\"basic-triangle.bin\""));

        byte[] binary = Files.readAllBytes(temporaryDirectory.resolve("basic-triangle.bin"));
        assertEquals(0, binary.length % 4);
        Matcher offsets = BYTE_OFFSET.matcher(json);
        while (offsets.find()) {
            assertEquals(0, Integer.parseInt(offsets.group(1)) % 4);
        }
        result.producedFiles().forEach(file -> {
            Path path = temporaryDirectory.resolve(file.relativePath());
            assertEquals(sha256(read(path)), file.sha256().orElseThrow());
            assertEquals(read(path).length, file.sizeBytes());
        });
    }

    @Test
    void exportsAlignedGlbContainer() throws Exception {
        GeneratedScene scene = scene(List.of(mesh("mesh/triangle", Optional.empty(), false)), List.of(), List.of());
        Path destination = temporaryDirectory.resolve("triangle.glb");
        ExportResult result = exporter.export(
                scene, destination, ExportOptions.EMPTY, ProgressReporter.NONE, CancellationToken.NONE);

        assertEquals(ExportStatus.SUCCESS, result.status());
        byte[] bytes = Files.readAllBytes(destination);
        ByteBuffer buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        assertEquals(0x46546c67, buffer.getInt());
        assertEquals(2, buffer.getInt());
        assertEquals(bytes.length, buffer.getInt());
        int jsonLength = buffer.getInt();
        assertEquals(0, jsonLength % 4);
        assertEquals(0x4e4f534a, buffer.getInt());
        byte[] jsonBytes = new byte[jsonLength];
        buffer.get(jsonBytes);
        assertTrue(new String(jsonBytes, StandardCharsets.UTF_8).trim().startsWith("{"));
        int binaryLength = buffer.getInt();
        assertEquals(0, binaryLength % 4);
        assertEquals(0x004e4942, buffer.getInt());
        assertEquals(binaryLength, buffer.remaining());
    }

    @Test
    void preservesHierarchyAndDistinctMeshGroups() throws Exception {
        MeshGroup first = mesh("mesh/first", Optional.empty(), false);
        MeshGroup second = mesh("mesh/second", Optional.empty(), false);
        SceneNode child = new SceneNode(
                "node/child", "Child", Optional.of("node/root"), Transform.IDENTITY,
                List.of("mesh/first", "mesh/second"), List.of(), Map.of(), List.of());
        SceneNode root = new SceneNode(
                "node/root", "Root", Optional.empty(), Transform.IDENTITY,
                List.of(), List.of("node/child"), Map.of(), List.of());
        GeneratedScene scene = sceneWithNodes(List.of(first, second), List.of(), List.of(root, child), List.of());

        Path destination = temporaryDirectory.resolve("hierarchy.glb");
        assertEquals(ExportStatus.SUCCESS, exporter.export(
                scene, destination, ExportOptions.EMPTY, ProgressReporter.NONE, CancellationToken.NONE).status());
        String json = glbJson(Files.readAllBytes(destination));
        assertEquals(2, occurrences(json, "\"primitives\":"));
        assertTrue(json.contains("node/child/mesh-attachment/mesh/first"));
        assertTrue(json.contains("node/child/mesh-attachment/mesh/second"));
    }

    @Test
    void mapsPbrAlphaEmissionAndVertexColours() throws Exception {
        MaterialDefinition material = material();
        GeneratedScene scene = scene(
                List.of(mesh("mesh/material", Optional.of(material.stableId()), true)),
                List.of(material), List.of());
        Path destination = temporaryDirectory.resolve("material.glb");
        assertEquals(ExportStatus.SUCCESS, exporter.export(
                scene, destination, ExportOptions.EMPTY, ProgressReporter.NONE, CancellationToken.NONE).status());
        String json = glbJson(Files.readAllBytes(destination));
        assertTrue(json.contains("\"baseColorFactor\":[1.0,0.25,0.0,0.5]"));
        assertTrue(json.contains("\"alphaMode\":\"BLEND\""));
        assertTrue(json.contains("\"COLOR_0\":"));
        assertTrue(json.contains("KHR_materials_emissive_strength"));
        assertTrue(json.contains("\"emissiveStrength\":2.0"));
    }

    @Test
    void appliesConfiguredTransformAtExporterBoundary() throws Exception {
        GeneratedScene scene = scene(List.of(mesh("mesh/triangle", Optional.empty(), false)), List.of(), List.of());
        ExportOptions options = new ExportOptions(Map.of(
                new CanonicalIdentifier("mcad", "gltf/translation"), numbers(2.0, 3.0, 4.0),
                new CanonicalIdentifier("mcad", "gltf/rotation"), numbers(0.0, 0.0, 0.0, 2.0),
                new CanonicalIdentifier("mcad", "gltf/scale"), numbers(0.5, 2.0, -1.0)));
        Path destination = temporaryDirectory.resolve("transform.glb");

        ExportResult result = exporter.export(
                scene, destination, options, ProgressReporter.NONE, CancellationToken.NONE);
        assertEquals(ExportStatus.SUCCESS, result.status());
        String json = glbJson(Files.readAllBytes(destination));
        assertTrue(json.contains("\"name\":\"m+CAD Export Transform\""));
        assertTrue(json.contains("\"translation\":[2.0,3.0,4.0]"));
        assertTrue(json.contains("\"rotation\":[0.0,0.0,0.0,1.0]"));
        assertTrue(json.contains("\"scale\":[0.5,2.0,-1.0]"));
        assertEquals(Transform.IDENTITY, scene.nodes().getFirst().localTransform());
    }
}
