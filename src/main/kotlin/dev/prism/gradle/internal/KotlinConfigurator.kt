package dev.prism.gradle.internal

import dev.prism.gradle.dsl.VersionConfiguration
import org.gradle.api.Project

object KotlinConfigurator {
    fun apply(project: Project, versionConfig: VersionConfiguration) {
        val kotlinVersion = versionConfig.kotlinVersion ?: return

        project.pluginManager.apply("org.jetbrains.kotlin.jvm")

        project.tasks.matching { it.javaClass.name.contains("KotlinCompile") }.configureEach { task ->
            try {
                val compilerOptions = task.javaClass.getMethod("getCompilerOptions").invoke(task)
                val jvmTarget = compilerOptions.javaClass.getMethod("getJvmTarget").invoke(compilerOptions)
                val targetString = if (versionConfig.resolvedJavaVersion >= 21) "21"
                    else if (versionConfig.resolvedJavaVersion >= 17) "17"
                    else versionConfig.resolvedJavaVersion.toString()

                val jvmTargetClass = task.javaClass.classLoader.loadClass("org.jetbrains.kotlin.gradle.dsl.JvmTarget")
                val fromTarget = jvmTargetClass.getMethod("fromTarget", String::class.java)
                val targetValue = fromTarget.invoke(null, targetString)
                jvmTarget.javaClass.getMethod("set", Any::class.java).invoke(jvmTarget, targetValue)
            } catch (_: Exception) {
            }
        }
    }
}
