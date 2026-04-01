package dev.prism.gradle.internal

import dev.prism.gradle.dsl.MetadataExtension
import dev.prism.gradle.dsl.RepositoryEntry
import dev.prism.gradle.dsl.VersionConfiguration
import net.neoforged.moddevgradle.legacyforge.dsl.LegacyForgeExtension
import net.neoforged.moddevgradle.dsl.NeoForgeExtension
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.jvm.toolchain.JavaLanguageVersion

object CommonConfigurator {

    private fun hasNeoForm(minecraftVersion: String): Boolean {
        val parts = minecraftVersion.split(".")
        val major = parts.getOrNull(0)?.toIntOrNull() ?: 1
        val minor = parts.getOrNull(1)?.toIntOrNull() ?: 0
        val patch = parts.getOrNull(2)?.toIntOrNull() ?: 0
        if (major > 1) return true
        if (minor > 20) return true
        if (minor == 20 && patch >= 2) return true
        return false
    }

    fun configure(
        commonProject: Project,
        versionConfig: VersionConfiguration,
        metadata: MetadataExtension,
        extraRepositories: List<RepositoryEntry> = emptyList(),
    ) {
        commonProject.pluginManager.apply("java-library")

        RepositorySetup.configure(commonProject, extraRepositories)

        commonProject.extensions.configure(JavaPluginExtension::class.java) { java ->
            java.toolchain.languageVersion.set(
                JavaLanguageVersion.of(versionConfig.resolvedJavaVersion)
            )
            java.withSourcesJar()
        }

        if (hasNeoForm(versionConfig.minecraftVersion)) {
            applyWithNeoForm(commonProject, versionConfig)
        } else {
            applyWithLegacyMcp(commonProject, versionConfig)
        }

        commonProject.dependencies.add("compileOnly", "org.spongepowered:mixin:0.8.5")
        commonProject.dependencies.add("compileOnly", "io.github.llamalad7:mixinextras-common:0.3.5")
        commonProject.dependencies.add("annotationProcessor", "io.github.llamalad7:mixinextras-common:0.3.5")

        val commonJava = commonProject.configurations.create("commonJava") { cfg ->
            cfg.isCanBeResolved = false
            cfg.isCanBeConsumed = true
        }

        val commonResources = commonProject.configurations.create("commonResources") { cfg ->
            cfg.isCanBeResolved = false
            cfg.isCanBeConsumed = true
        }

        commonProject.afterEvaluate { proj ->
            val javaExt = proj.extensions.getByType(JavaPluginExtension::class.java)
            val mainSourceSet = javaExt.sourceSets.getByName("main")
            proj.artifacts.add("commonJava", mainSourceSet.java.sourceDirectories.singleFile)
            proj.artifacts.add("commonResources", mainSourceSet.resources.sourceDirectories.singleFile)
        }

        val group = metadata.group.ifEmpty { commonProject.rootProject.group.toString() }
        listOf("apiElements", "runtimeElements", "sourcesElements", "javadocElements").forEach { variant ->
            commonProject.configurations.findByName(variant)?.outgoing { outgoing ->
                outgoing.capability("$group:${metadata.modId}:${commonProject.version}")
            }
        }

        TemplateExpansion.configure(commonProject, versionConfig, metadata)
    }

    private fun applyWithNeoForm(project: Project, versionConfig: VersionConfiguration) {
        project.pluginManager.apply("net.neoforged.moddev")

        val neoFormVersion = versionConfig.neoFormVersion
            ?: NeoFormVersionResolver.resolveNeoForm(versionConfig.minecraftVersion, project)

        project.extensions.configure(NeoForgeExtension::class.java) { neoForge ->
            neoForge.neoFormVersion = neoFormVersion

            if (versionConfig.parchmentMinecraftVersion != null && versionConfig.parchmentMappingsVersion != null) {
                neoForge.parchment { parchment ->
                    parchment.minecraftVersion.set(versionConfig.parchmentMinecraftVersion)
                    parchment.mappingsVersion.set(versionConfig.parchmentMappingsVersion)
                }
            }

            val at = project.file("src/main/resources/META-INF/accesstransformer.cfg")
            if (at.exists()) {
                neoForge.accessTransformers.from(at.absolutePath)
            }
        }
    }

    private fun applyWithLegacyMcp(project: Project, versionConfig: VersionConfiguration) {
        project.pluginManager.apply("net.neoforged.moddev.legacyforge")

        project.extensions.configure(LegacyForgeExtension::class.java) { legacyForge ->
            legacyForge.mcpVersion = versionConfig.minecraftVersion

            if (versionConfig.parchmentMinecraftVersion != null && versionConfig.parchmentMappingsVersion != null) {
                legacyForge.parchment { parchment ->
                    parchment.minecraftVersion.set(versionConfig.parchmentMinecraftVersion)
                    parchment.mappingsVersion.set(versionConfig.parchmentMappingsVersion)
                }
            }

            val at = project.file("src/main/resources/META-INF/accesstransformer.cfg")
            if (at.exists()) {
                legacyForge.setAccessTransformers(at.absolutePath)
            }
        }
    }
}
