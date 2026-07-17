plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "m-plus-CAD"

include(
    "mcad-api",
    "mcad-core",
    "mcad-materials",
    "mcad-markers",
    "mcad-export-obj",
    "mcad-export-gltf",
    "mcad-minecraft",
)
