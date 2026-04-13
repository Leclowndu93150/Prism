package dev.prism.gradle.internal

import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
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

        project.afterEvaluate {
            val reobfJarTask = project.tasks.findByName("reobfJar") as? AbstractArchiveTask

            project.tasks.named("shadowJar", ShadowJar::class.java).configure { shadowJar ->
                shadowJar.configurations.set(listOf(shadowConfig))
                shadowJar.mergeServiceFiles()
                shadowJar.exclude("META-INF/INDEX.LIST", "META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA", "module-info.class")

                if (reobfJarTask != null) {
                    shadowJar.dependsOn(reobfJarTask)
                    shadowJar.from(project.zipTree(reobfJarTask.archiveFile))
                    shadowJar.archiveClassifier.set("")
                    shadowJar.destinationDirectory.set(project.layout.buildDirectory.dir("libs"))

                    project.tasks.named("jar", Jar::class.java).configure { jar ->
                        jar.archiveClassifier.set("dev")
                    }
                } else {
                    shadowJar.archiveClassifier.set("")
                    project.tasks.named("jar", Jar::class.java).configure { jar ->
                        jar.archiveClassifier.set("slim")
                    }
                }
            }

            project.tasks.named("assemble").configure { assemble ->
                assemble.dependsOn("shadowJar")
            }
        }
    }
}
