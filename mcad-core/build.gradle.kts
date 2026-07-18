/*
 * SPDX-License-Identifier: MPL-2.0
 */
description = "Loader-independent geometry and optimization engine for m+CAD"

dependencies {
    api(project(":mcad-api"))

    testImplementation(platform("org.junit:junit-bom:6.1.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
