plugins {
    `java-library`
}

description = "glTF and GLB exporter for m+CAD generated scenes"

dependencies {
    api(project(":mcad-api"))

    testImplementation(platform("org.junit:junit-bom:6.1.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
