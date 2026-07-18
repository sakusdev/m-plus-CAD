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

dependencies {
    minecraft("com.mojang:minecraft:${minecraftVersion.get()}")
    implementation("net.fabricmc:fabric-loader:${loaderVersion.get()}")
    implementation("net.fabricmc.fabric-api:fabric-api:${fabricApiVersion.get()}")

    api(project(":mcad-api"))

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
    inputs.property("version", project.version)
    filesMatching("fabric.mod.json") {
        expand("version" to project.version)
    }
}
