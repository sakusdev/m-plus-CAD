/*
 * SPDX-License-Identifier: MPL-2.0
 */
package dev.sakus.mcad.export.obj;

import dev.sakus.mcad.api.CancellationToken;
import dev.sakus.mcad.api.MaterialDefinition;
import dev.sakus.mcad.api.MeshPrimitive;
import dev.sakus.mcad.api.ProgressReporter;
import dev.sakus.mcad.api.ProgressUpdate;
import dev.sakus.mcad.api.Vec3d;

import java.io.BufferedWriter;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.OptionalLong;

final class ObjOutputWriter {
    private static final int CANCELLATION_INTERVAL = 4_096;
    private static final double ZERO_EPSILON = 1.0e-12;

    private ObjOutputWriter() {
    }

    static ProgressTracker progress(ProgressReporter reporter, long total) {
        return new ProgressTracker(reporter, Math.max(1L, total));
    }

    static void writeMtl(
            Path temporary,
            ObjExportPlan plan,
            CancellationToken cancellation,
            ProgressTracker tracker) throws IOException {
        try (BufferedWriter writer = Files.newBufferedWriter(
                temporary, StandardCharsets.UTF_8,
                StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE)) {
            line(writer, "# m+CAD deterministic Wavefront MTL");
            line(writer, "# Minecraft/Mojang/Microsoft textures are not referenced or generated.");
            for (MaterialDefinition material : plan.materials()) {
                cancellation.throwIfCancellationRequested();
                line(writer, "");
                line(writer, "newmtl " + plan.materialNames().get(material.stableId()));
                line(writer, "Ka 0 0 0");
                line(writer, "Kd " + number(material.baseColour().red()) + " "
                        + number(material.baseColour().green()) + " "
                        + number(material.baseColour().blue()));
                line(writer, "Ks "
                        + number(material.baseColour().red() * material.metallic()) + " "
                        + number(material.baseColour().green() * material.metallic()) + " "
                        + number(material.baseColour().blue() * material.metallic()));
                line(writer, "Ns " + number(Math.max(
                        0.0, Math.min(1000.0, (1.0 - material.roughness()) * 1000.0))));
                line(writer, "d " + number(material.baseColour().alpha()));
                line(writer, "illum 2");
                line(writer, "# mcad_stable_id " + comment(material.stableId()));
                line(writer, "# mcad_metallic " + number(material.metallic()));
                line(writer, "# mcad_roughness " + number(material.roughness()));
                line(writer, "# mcad_emissive "
                        + number(material.emissiveColour().red()) + " "
                        + number(material.emissiveColour().green()) + " "
                        + number(material.emissiveColour().blue()) + " "
                        + number(material.emissiveStrength()));
                line(writer, "# mcad_alpha_mode " + material.alphaMode().name());
                if (material.alphaCutoff().isPresent()) {
                    line(writer, "# mcad_alpha_cutoff " + number(material.alphaCutoff().getAsDouble()));
                }
                tracker.tick("Serialized material " + material.stableId());
            }
        }
    }

    static void writeObj(
            Path temporary,
            String mtlFileName,
            ObjExportPlan plan,
            CancellationToken cancellation,
            ProgressTracker tracker) throws IOException {
        try (BufferedWriter writer = Files.newBufferedWriter(
                temporary, StandardCharsets.UTF_8,
                StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE)) {
            line(writer, "# m+CAD deterministic Wavefront OBJ");
            line(writer, "# Minecraft/Mojang/Microsoft textures are not referenced or generated.");
            line(writer, "mtllib " + token(mtlFileName));

            long vertexOffset = 0;
            long normalOffset = 0;
            long operationCount = 0;
            for (ObjExportPlan.MeshInstance instance : plan.instances()) {
                cancellation.throwIfCancellationRequested();
                line(writer, "");
                line(writer, "# mcad_mesh_id " + comment(instance.mesh().stableId()));
                if (!instance.nodeId().isEmpty()) {
                    line(writer, "# mcad_node_id " + comment(instance.nodeId()));
                }
                line(writer, "o " + instance.objectName());
                line(writer, "g " + instance.objectName());

                boolean reverseWinding = instance.transform().determinant() < 0.0;
                for (MeshPrimitive primitive : instance.mesh().primitives()) {
                    String materialName = primitive.materialId().map(plan.materialNames()::get).orElse(null);
                    line(writer, materialName == null ? "usemtl off" : "usemtl " + materialName);

                    for (Vec3d position : primitive.positions()) {
                        Vec3d transformed = instance.transform().applyPoint(position);
                        line(writer, "v " + number(transformed.x()) + " "
                                + number(transformed.y()) + " " + number(transformed.z()));
                        operationCount++;
                        if ((operationCount % CANCELLATION_INTERVAL) == 0) {
                            cancellation.throwIfCancellationRequested();
                        }
                        tracker.tick("Serialized OBJ vertices");
                    }
                    for (Vec3d normal : primitive.normals()) {
                        Vec3d transformed = instance.transform().applyNormal(normal);
                        line(writer, "vn " + number(transformed.x()) + " "
                                + number(transformed.y()) + " " + number(transformed.z()));
                    }

                    for (int index = 0; index < primitive.indices().size(); index += 3) {
                        int first = primitive.indices().get(index);
                        int second = primitive.indices().get(index + 1);
                        int third = primitive.indices().get(index + 2);
                        if (reverseWinding) {
                            int swap = second;
                            second = third;
                            third = swap;
                        }
                        line(writer, "f "
                                + face(first, vertexOffset, normalOffset) + " "
                                + face(second, vertexOffset, normalOffset) + " "
                                + face(third, vertexOffset, normalOffset));
                        operationCount++;
                        if ((operationCount % CANCELLATION_INTERVAL) == 0) {
                            cancellation.throwIfCancellationRequested();
                        }
                        tracker.tick("Serialized OBJ triangles");
                    }
                    vertexOffset = Math.addExact(vertexOffset, primitive.positions().size());
                    normalOffset = Math.addExact(normalOffset, primitive.normals().size());
                }
            }
        }
    }

    private static String face(int localIndex, long vertexOffset, long normalOffset) {
        long vertex = Math.addExact(vertexOffset, localIndex + 1L);
        long normal = Math.addExact(normalOffset, localIndex + 1L);
        return vertex + "//" + normal;
    }

    private static String token(String value) {
        StringBuilder result = new StringBuilder(value.length() + 8);
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            if (character == ' ' || character == '\t' || character == '#' || character == '\\') {
                result.append('\\');
            }
            result.append(character);
        }
        return result.toString();
    }

    private static String comment(String value) {
        return ObjDiagnostics.bounded(value.replace('\r', ' ').replace('\n', ' '));
    }

    private static String number(double value) {
        double normalized = Math.abs(value) < ZERO_EPSILON ? 0.0 : value;
        BigDecimal decimal = BigDecimal.valueOf(normalized).stripTrailingZeros();
        String result = decimal.toPlainString();
        return result.equals("-0") ? "0" : result;
    }

    private static void line(BufferedWriter writer, String value) throws IOException {
        writer.write(value);
        writer.write('\n');
    }

    static final class ProgressTracker {
        private final ProgressReporter reporter;
        private final long total;
        private long completed;
        private long lastReported;

        private ProgressTracker(ProgressReporter reporter, long total) {
            this.reporter = reporter;
            this.total = total;
        }

        void tick(String message) {
            if (completed < total) {
                completed++;
            }
            if (completed == total || completed - lastReported >= CANCELLATION_INTERVAL) {
                report(message);
            }
        }

        void report(String message) {
            reporter.report(new ProgressUpdate(
                    "export.obj", completed, OptionalLong.of(total), message));
            lastReported = completed;
        }

        void finish() {
            completed = total;
            report("OBJ/MTL export complete");
        }
    }
}
