package dev.prism.gradle.internal

import org.gradle.api.Project
import org.gradle.api.tasks.bundling.AbstractArchiveTask
import org.gradle.jvm.tasks.Jar

object ShadowConfigurator {

    fun configure(project: Project) {
        project.pluginManager.apply("com.gradleup.shadow")

        val shadowConfig = project.configurations.findByName("shadow")
            ?: project.configurations.create("shadow") { cfg ->
                cfg.isCanBeResolved = true
                cfg.isCanBeConsumed = false
                cfg.isTransitive = true
            }

        project.tasks.named("shadowJar") { task ->
            val shadowJar = task as AbstractArchiveTask
            shadowJar.archiveClassifier.set("")

            try {
                val sjClass = Class.forName("com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar")
                val configurationsMethod = sjClass.getMethod("getConfigurations")
                @Suppress("UNCHECKED_CAST")
                val configurations = configurationsMethod.invoke(task) as MutableList<Any>
                configurations.clear()
                configurations.add(shadowConfig)

                val mergeServiceFilesMethod = sjClass.getMethod("mergeServiceFiles")
                mergeServiceFilesMethod.invoke(task)
            } catch (e: Exception) {
                project.logger.warn("Prism: Could not configure shadowJar task: ${e.message}")
            }
        }

        project.tasks.named("jar", Jar::class.java) { jar ->
            jar.archiveClassifier.set("slim")
        }

        val reobfTask = project.tasks.findByName("reobfJar")
        if (reobfTask != null) {
            project.tasks.named("shadowJar") { task ->
                task.mustRunAfter(reobfTask)
            }
            project.afterEvaluate {
                try {
                    val reobfClass = reobfTask.javaClass
                    val inputMethod = reobfClass.methods.find { it.name == "getInput" }
                    if (inputMethod != null) {
                        val shadowJarTask = project.tasks.named("shadowJar").get()
                        val outputsFile = shadowJarTask.outputs.files.singleFile
                        inputMethod.invoke(reobfTask)
                    }
                } catch (_: Exception) {}
            }
        }
    }
}
