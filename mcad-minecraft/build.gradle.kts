/*
 * SPDX-License-Identifier: MPL-2.0
 */
plugins {
    id("net.fabricmc.fabric-loom")
}

description = "Minecraft 26.2 Fabric client adapter for m+CAD"

val minecraftVersion = providers.gradleProperty("minecraft_version")
val loaderVersion = providers.gradleProperty("loader_version")
val fabricApiVersion = providers.gradleProperty("fabric_api_version")
val modVersion = version.toString()

dependencies {
    minecraft("com.mojang:minecraft:${minecraftVersion.get()}")
    implementation("net.fabricmc:fabric-loader:${loaderVersion.get()}")
    implementation("net.fabricmc.fabric-api:fabric-api:${fabricApiVersion.get()}")

    api(project(":mcad-api"))
    implementation(project(":mcad-core"))
    implementation(project(":mcad-materials"))
    implementation(project(":mcad-markers"))
    implementation(project(":mcad-export-obj"))
    implementation(project(":mcad-export-gltf"))
    implementation(project(":mcad-live-link"))

    include(project(":mcad-api"))
    include(project(":mcad-core"))
    include(project(":mcad-materials"))
    include(project(":mcad-markers"))
    include(project(":mcad-export-obj"))
    include(project(":mcad-export-gltf"))
    include(project(":mcad-live-link"))

    testImplementation(platform("org.junit:junit-bom:6.1.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("net.fabricmc:fabric-loader-junit:${loaderVersion.get()}")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

fabricApi {
    configureTests {
        enableGameTests = true
        enableClientGameTests = true
    }
}

tasks.processResources {
    inputs.property("version", modVersion)
    filesMatching("fabric.mod.json") {
        expand("version" to modVersion)
    }
}
