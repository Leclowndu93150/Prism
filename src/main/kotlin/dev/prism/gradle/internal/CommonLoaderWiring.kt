package dev.prism.gradle.internal

import dev.prism.gradle.dsl.MetadataExtension
import org.gradle.api.Project
import org.gradle.api.artifacts.ModuleDependency
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.jvm.tasks.Jar
import org.gradle.language.jvm.tasks.ProcessResources

object CommonLoaderWiring {
    fun wire(loaderProject: Project, commonProject: Project, metadata: MetadataExtension) {
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
        }
    }
}
