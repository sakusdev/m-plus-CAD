/*
 * SPDX-License-Identifier: MPL-2.0
 */

description = "Versioned Minecraft-to-DCC live-link protocol and loopback transport"

dependencies {
    api(project(":mcad-api"))

    testImplementation(platform("org.junit:junit-bom:6.1.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
