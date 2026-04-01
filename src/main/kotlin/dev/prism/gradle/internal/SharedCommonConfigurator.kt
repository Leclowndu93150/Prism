package dev.prism.gradle.internal

import dev.prism.gradle.dsl.MetadataExtension
import dev.prism.gradle.dsl.RepositoryEntry
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.jvm.toolchain.JavaLanguageVersion

object SharedCommonConfigurator {

    fun configure(
        sharedProject: Project,
        metadata: MetadataExtension,
        extraRepositories: List<RepositoryEntry>,
        javaVersion: Int,
    ) {
        sharedProject.pluginManager.apply("java-library")

        RepositorySetup.configure(sharedProject, extraRepositories)

        sharedProject.extensions.configure(JavaPluginExtension::class.java) { java ->
            java.toolchain.languageVersion.set(JavaLanguageVersion.of(javaVersion))
            java.withSourcesJar()
        }

        val sharedJava = sharedProject.configurations.create("sharedJava") { cfg ->
            cfg.isCanBeResolved = false
            cfg.isCanBeConsumed = true
        }

        val sharedResources = sharedProject.configurations.create("sharedResources") { cfg ->
            cfg.isCanBeResolved = false
            cfg.isCanBeConsumed = true
        }

        sharedProject.afterEvaluate { proj ->
            val javaExt = proj.extensions.getByType(JavaPluginExtension::class.java)
            val mainSourceSet = javaExt.sourceSets.getByName("main")
            proj.artifacts.add("sharedJava", mainSourceSet.java.sourceDirectories.singleFile)
            proj.artifacts.add("sharedResources", mainSourceSet.resources.sourceDirectories.singleFile)
        }
    }

    fun wireInto(versionCommonProject: Project, sharedProject: Project) {
        val sharedJava = versionCommonProject.configurations.create("sharedJava") { cfg ->
            cfg.isCanBeResolved = true
            cfg.isCanBeConsumed = false
        }

        val sharedResources = versionCommonProject.configurations.create("sharedResources") { cfg ->
            cfg.isCanBeResolved = true
            cfg.isCanBeConsumed = false
        }

        versionCommonProject.dependencies.add("compileOnly", versionCommonProject.dependencies.project(
            mapOf("path" to sharedProject.path)
        ))

        versionCommonProject.dependencies.add(
            "sharedJava",
            versionCommonProject.dependencies.project(
                mapOf("path" to sharedProject.path, "configuration" to "sharedJava")
            )
        )

        versionCommonProject.dependencies.add(
            "sharedResources",
            versionCommonProject.dependencies.project(
                mapOf("path" to sharedProject.path, "configuration" to "sharedResources")
            )
        )

        versionCommonProject.tasks.named("compileJava", org.gradle.api.tasks.compile.JavaCompile::class.java) { task ->
            task.dependsOn(sharedJava)
            task.source(sharedJava)
        }

        versionCommonProject.tasks.named("processResources", org.gradle.language.jvm.tasks.ProcessResources::class.java) { task ->
            task.dependsOn(sharedResources)
            task.from(sharedResources)
        }

        versionCommonProject.tasks.matching { it.name == "sourcesJar" }.configureEach { task ->
            task.dependsOn(sharedJava)
            task as org.gradle.jvm.tasks.Jar
            task.from(sharedJava)
            task.dependsOn(sharedResources)
            task.from(sharedResources)
        }
    }
}
