package dev.prism.gradle.internal

import org.gradle.api.Project
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.tasks.testing.Test
import org.gradle.api.tasks.testing.junitplatform.JUnitPlatformOptions

object TestConfigurator {
    fun apply(project: Project, junitVersion: String, nativesMinecraftVersion: String?) {
        project.dependencies.add("testImplementation", project.dependencies.platform("org.junit:junit-bom:$junitVersion"))
        project.dependencies.add("testImplementation", "org.junit.jupiter:junit-jupiter")
        project.dependencies.add("testRuntimeOnly", "org.junit.platform:junit-platform-launcher")

        if (nativesMinecraftVersion != null) {
            for (native in MinecraftLibraries.hostNatives(project, nativesMinecraftVersion)) {
                project.dependencies.add("testRuntimeOnly", native)
            }
        }

        val sourceSets = project.extensions.getByType(JavaPluginExtension::class.java).sourceSets
        val main = sourceSets.getByName("main")
        sourceSets.named("test") { test ->
            test.compileClasspath += main.compileClasspath
            test.runtimeClasspath += main.compileClasspath
        }

        project.tasks.withType(Test::class.java).configureEach { task ->
            if (task.options !is JUnitPlatformOptions) task.useJUnitPlatform()
        }
    }
}
