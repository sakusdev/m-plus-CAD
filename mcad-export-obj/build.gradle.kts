/*
 * SPDX-License-Identifier: MPL-2.0
 */
description = "OBJ and MTL exporter for m+CAD generated scenes"

dependencies {
    api(project(":mcad-api"))

    testImplementation(platform("org.junit:junit-bom:6.1.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
