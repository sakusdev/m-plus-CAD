plugins {
    `java-library`
}

description = "Minecraft-independent material mappings and asset validation for m+CAD"

dependencies {
    api(project(":mcad-api"))

    testImplementation(platform("org.junit:junit-bom:6.1.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
