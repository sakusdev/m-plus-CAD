import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.api.tasks.testing.Test
import org.gradle.jvm.toolchain.JavaLanguageVersion

plugins {
    base
}

group = "dev.sakus.mcad"
version = "0.1.0-SNAPSHOT"

subprojects {
    apply(plugin = "java-library")

    group = rootProject.group
    version = rootProject.version

    repositories {
        mavenCentral()
    }

    extensions.configure<JavaPluginExtension> {
        toolchain.languageVersion.set(JavaLanguageVersion.of(25))
        withSourcesJar()
        withJavadocJar()
    }

    tasks.withType<JavaCompile>().configureEach {
        options.release.set(25)
        options.encoding = "UTF-8"
        options.compilerArgs.addAll(listOf("-Xlint:all", "-Werror"))
    }

    tasks.withType<Test>().configureEach {
        useJUnitPlatform()
        failFast = false
        systemProperty("file.encoding", "UTF-8")
    }
}

project(":mcad-api") {
    dependencies {
        "testImplementation"(platform("org.junit:junit-bom:6.1.0"))
        "testImplementation"("org.junit.jupiter:junit-jupiter")
        "testRuntimeOnly"("org.junit.platform:junit-platform-launcher")
    }
}

val verifyApiIsolation by tasks.registering {
    group = "verification"
    description = "Reject Minecraft, loader, exporter implementation, GUI, or Blender dependencies from mcad-api."

    dependsOn(":mcad-api:classes")

    doLast {
        val forbidden = listOf("minecraft", "fabric", "neoforge", "forge", "blender")
        val apiProject = project(":mcad-api")
        val runtime = apiProject.configurations.getByName("runtimeClasspath")
        val forbiddenComponents = runtime.incoming.resolutionResult.allComponents.mapNotNull { component ->
            val id = component.id
            if (id is ModuleComponentIdentifier) {
                val coordinate = "${id.group}:${id.module}:${id.version}".lowercase()
                coordinate.takeIf { candidate -> forbidden.any(candidate::contains) }
            } else {
                null
            }
        }
        check(forbiddenComponents.isEmpty()) {
            "mcad-api runtime dependency graph contains forbidden components: $forbiddenComponents"
        }

        val forbiddenImports = listOf(
            "net.minecraft",
            "net.fabricmc",
            "net.neoforged",
            "net.minecraftforge",
            "org.blender",
        )
        val sourceRoot = apiProject.file("src/main/java")
        val violations = sourceRoot.walkTopDown()
            .filter { it.isFile && it.extension == "java" }
            .toList()
            .flatMap { file ->
                file.readLines().mapIndexedNotNull { index, line ->
                    forbiddenImports.firstOrNull(line::contains)?.let { token ->
                        "${file.relativeTo(apiProject.projectDir)}:${index + 1}: $token"
                    }
                }
            }
        check(violations.isEmpty()) {
            "mcad-api sources contain forbidden platform references:\n${violations.joinToString("\n")}"
        }
    }
}

tasks.named("check") {
    dependsOn(subprojects.map { it.tasks.named("check") })
    dependsOn(verifyApiIsolation)
}
