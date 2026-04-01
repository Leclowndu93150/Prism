package dev.prism.gradle.internal

import dev.prism.gradle.dsl.MetadataExtension
import org.gradle.api.Project
import org.gradle.api.artifacts.ModuleDependency
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.jvm.tasks.Jar
import org.gradle.language.jvm.tasks.ProcessResources

object CommonLoaderWiring {
    fun wire(loaderProject: Project, commonProject: Project, metadata: MetadataExtension, sharedProject: Project? = null) {
        val commonJava = loaderProject.configurations.create("commonJava") { cfg ->
            cfg.isCanBeResolved = true
            cfg.isCanBeConsumed = false
        }

        val commonResources = loaderProject.configurations.create("commonResources") { cfg ->
            cfg.isCanBeResolved = true
            cfg.isCanBeConsumed = false
        }

        val commonDep = loaderProject.dependencies.project(
            mapOf("path" to commonProject.path)
        )
        (commonDep as ModuleDependency).capabilities { caps ->
            caps.requireCapability("${metadata.group.ifEmpty { loaderProject.rootProject.group }}:${metadata.modId}")
        }
        loaderProject.dependencies.add("compileOnly", commonDep)

        loaderProject.dependencies.add(
            "commonJava",
            loaderProject.dependencies.project(
                mapOf("path" to commonProject.path, "configuration" to "commonJava")
            )
        )

        loaderProject.dependencies.add(
            "commonResources",
            loaderProject.dependencies.project(
                mapOf("path" to commonProject.path, "configuration" to "commonResources")
            )
        )

        if (sharedProject != null) {
            val sharedJava = loaderProject.configurations.create("sharedJava") { cfg ->
                cfg.isCanBeResolved = true
                cfg.isCanBeConsumed = false
            }

            val sharedResources = loaderProject.configurations.create("sharedResources") { cfg ->
                cfg.isCanBeResolved = true
                cfg.isCanBeConsumed = false
            }

            loaderProject.dependencies.add("compileOnly", loaderProject.dependencies.project(
                mapOf("path" to sharedProject.path)
            ))

            loaderProject.dependencies.add(
                "sharedJava",
                loaderProject.dependencies.project(
                    mapOf("path" to sharedProject.path, "configuration" to "sharedJava")
                )
            )

            loaderProject.dependencies.add(
                "sharedResources",
                loaderProject.dependencies.project(
                    mapOf("path" to sharedProject.path, "configuration" to "sharedResources")
                )
            )

            loaderProject.tasks.named("compileJava", JavaCompile::class.java) { task ->
                task.dependsOn(sharedJava)
                task.source(sharedJava)
            }

            loaderProject.tasks.named("processResources", ProcessResources::class.java) { task ->
                task.dependsOn(sharedResources)
                task.from(sharedResources)
            }
        }

        loaderProject.tasks.named("compileJava", JavaCompile::class.java) { task ->
            task.dependsOn(commonJava)
            task.source(commonJava)
        }

        loaderProject.tasks.named("processResources", ProcessResources::class.java) { task ->
            task.dependsOn(commonResources)
            task.from(commonResources)
        }

        loaderProject.tasks.matching { it.name == "javadoc" }.configureEach { task ->
            task.dependsOn(commonJava)
            task as org.gradle.api.tasks.javadoc.Javadoc
            task.source(commonJava)
        }

        loaderProject.tasks.matching { it.name == "sourcesJar" }.configureEach { task ->
            task.dependsOn(commonJava)
            task as Jar
            task.from(commonJava)
            task.dependsOn(commonResources)
            task.from(commonResources)
            if (sharedProject != null) {
                val sharedJava = loaderProject.configurations.getByName("sharedJava")
                val sharedResources = loaderProject.configurations.getByName("sharedResources")
                task.dependsOn(sharedJava)
                task.from(sharedJava)
                task.dependsOn(sharedResources)
                task.from(sharedResources)
            }
        }
    }
}
