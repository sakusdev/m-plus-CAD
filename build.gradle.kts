import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.artifacts.component.ProjectComponentIdentifier
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

    val apiRuntimeClasspath = configurations.named("runtimeClasspath")
    val apiSourceRoot = layout.projectDirectory.dir("src/main/java")
    val apiProjectDirectory = layout.projectDirectory
    val apiProjectPath = path

    val verifyApiIsolation by tasks.registering {
        group = "verification"
        description = "Reject all non-JDK runtime dependencies and platform-specific source references from mcad-api."

        dependsOn("classes")

        doLast {
            val runtime = apiRuntimeClasspath.get()
            val forbiddenComponents = runtime.incoming.resolutionResult.allComponents.mapNotNull { component ->
                when (val id = component.id) {
                    is ModuleComponentIdentifier -> "module:${id.group}:${id.module}:${id.version}"
                    is ProjectComponentIdentifier -> id.projectPath
                        .takeIf { projectPath -> projectPath != apiProjectPath }
                        ?.let { projectPath -> "project:$projectPath" }
                    else -> null
                }
            }
            check(forbiddenComponents.isEmpty()) {
                "mcad-api runtime dependency graph must contain no external modules or sibling projects: " +
                        forbiddenComponents
            }

            val forbiddenImports = listOf(
                "net.minecraft",
                "net.fabricmc",
                "net.neoforged",
                "net.minecraftforge",
                "org.blender",
                "dev.sakus.mcad.core",
                "dev.sakus.mcad.materials",
                "dev.sakus.mcad.markers",
                "dev.sakus.mcad.export",
                "dev.sakus.mcad.minecraft",
                "dev.sakus.mcad.gui",
                "dev.sakus.mcad.blender",
            )
            val sourceRoot = apiSourceRoot.asFile
            val projectDirectory = apiProjectDirectory.asFile
            val violations = sourceRoot.walkTopDown()
                .filter { it.isFile && it.extension == "java" }
                .toList()
                .flatMap { file ->
                    file.readLines().mapIndexedNotNull { index, line ->
                        forbiddenImports.firstOrNull(line::contains)?.let { token ->
                            "${file.relativeTo(projectDirectory)}:${index + 1}: $token"
                        }
                    }
                }
            check(violations.isEmpty()) {
                "mcad-api sources contain forbidden platform or sibling-module references:\n" +
                        violations.joinToString("\n")
            }
        }
    }

    tasks.named("check") {
        dependsOn(verifyApiIsolation)
    }
}

tasks.named("check") {
    dependsOn(subprojects.map { it.tasks.named("check") })
}
