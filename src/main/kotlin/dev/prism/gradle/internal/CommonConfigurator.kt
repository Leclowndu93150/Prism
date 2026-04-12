package dev.prism.gradle.internal

import dev.prism.gradle.dsl.MetadataExtension
import dev.prism.gradle.dsl.RepositoryEntry
import dev.prism.gradle.dsl.VersionConfiguration
import dev.prism.gradle.internal.accesswidener.AccessWidenerSupport
import net.neoforged.moddevgradle.legacyforge.dsl.LegacyForgeExtension
import net.neoforged.moddevgradle.dsl.NeoForgeExtension
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.jvm.toolchain.JavaLanguageVersion

object CommonConfigurator {
    fun applyDownstreamSupportDeps(project: Project, versionConfig: VersionConfiguration) {
        project.dependencies.add("compileOnly", "org.spongepowered:mixin:0.8.5")

        val hasForge = versionConfig.forgeConfig != null
        if (!hasForge) {
            project.dependencies.add("compileOnly", "io.github.llamalad7:mixinextras-common:0.3.5")
            project.dependencies.add("annotationProcessor", "io.github.llamalad7:mixinextras-common:0.3.5")
        }
    }


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
            applyWithNeoForm(commonProject, versionConfig, metadata)
        } else {
            applyWithLegacyMcp(commonProject, versionConfig, metadata)
        }

        applyDownstreamSupportDeps(commonProject, versionConfig)

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
        val resolvedVersion = metadata.version.ifEmpty { commonProject.rootProject.version.toString() }
        val commonArtifactId = "${metadata.modId}-${versionConfig.minecraftVersion}-common"
        listOf("apiElements", "runtimeElements", "sourcesElements", "javadocElements").forEach { variant ->
            commonProject.configurations.findByName(variant)?.outgoing { outgoing ->
                outgoing.capability("$group:$commonArtifactId:$resolvedVersion")
                outgoing.capability("$group:${metadata.modId}:$resolvedVersion")
            }
        }

        TemplateExpansion.configure(commonProject, versionConfig, metadata)
    }

    private fun applyWithNeoForm(project: Project, versionConfig: VersionConfiguration, metadata: MetadataExtension) {
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
            if (AccessWidenerSupport.hasAccessTransformerEntries(at)) {
                neoForge.accessTransformers.from(at.absolutePath)
            } else {
                val awFile = AccessWidenerSupport.resolveAccessWidener(
                    project, null, versionConfig.unifiedAccessWidener, metadata.modId
                )
                if (awFile != null) {
                    val generatedAt = AccessWidenerSupport.generateAccessTransformer(project, awFile, "common")
                    neoForge.accessTransformers.from(generatedAt.absolutePath)
                }
            }
        }
    }

    private fun applyWithLegacyMcp(project: Project, versionConfig: VersionConfiguration, metadata: MetadataExtension) {
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
            if (AccessWidenerSupport.hasAccessTransformerEntries(at)) {
                legacyForge.setAccessTransformers(at.absolutePath)
            } else {
                val awFile = AccessWidenerSupport.resolveAccessWidener(
                    project, null, versionConfig.unifiedAccessWidener, metadata.modId
                )
                if (awFile != null) {
                    val generatedAt = AccessWidenerSupport.generateAccessTransformer(project, awFile, "common")
                    legacyForge.setAccessTransformers(generatedAt.absolutePath)
                }
            }
        }
    }
}
