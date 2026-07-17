/*
 * SPDX-License-Identifier: MPL-2.0
 */
package dev.sakus.mcad.api;


public final class ApiVersions {
    public static final SchemaVersion STRUCTURE_SNAPSHOT = new SchemaVersion(1, 0);
    public static final SchemaVersion GENERATED_SCENE = new SchemaVersion(1, 0);
    public static final SchemaVersion PROJECT_SETTINGS = new SchemaVersion(1, 0);
    public static final SchemaVersion EXPORTER_CONTRACT = new SchemaVersion(1, 0);

    private ApiVersions() {
    }
}
