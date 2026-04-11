package dev.prism.gradle.internal

import dev.prism.gradle.dsl.FabricConfiguration
import dev.prism.gradle.dsl.ForgeConfiguration
import dev.prism.gradle.dsl.LexForgeConfiguration
import dev.prism.gradle.dsl.LegacyForgeConfiguration
import dev.prism.gradle.dsl.LoaderConfiguration
import dev.prism.gradle.dsl.MixinOptions
import dev.prism.gradle.dsl.ModuleConfiguration
import dev.prism.gradle.dsl.NeoForgeConfiguration
import dev.prism.gradle.dsl.PublishingConfiguration
import dev.prism.gradle.dsl.PrismExtension
import dev.prism.gradle.dsl.VersionConfiguration
import org.gradle.api.Project

object PrismDoctor {
    fun register(rootProject: Project, extension: PrismExtension) {
        if (rootProject.tasks.findByName("prismDoctor") != null) return

        rootProject.tasks.register("prismDoctor") { task ->
            task.group = "help"
            task.description = "Prints a Prism configuration and wiring report"
            task.doLast {
                val report = buildString {
                    appendLine("Prism Doctor")
                    appendLine("root: ${rootProject.path}")
                    appendLine("sharedCommon: ${rootProject.findProject(":common") != null}")
                    appendLine()

                    for ((mcVersion, versionConfig) in extension.versions) {
                        appendVersionReport(rootProject, mcVersion, versionConfig, null, extension.publishingConfig)
                    }

                    for ((moduleName, moduleConfig) in extension.modules) {
                        appendLine("module: $moduleName")
                        for ((mcVersion, versionConfig) in moduleConfig.versions) {
                            appendVersionReport(rootProject, mcVersion, versionConfig, moduleConfig, moduleConfig.publishingConfig)
                        }
                        appendLine()
                    }
                }

                rootProject.logger.lifecycle(report.trimEnd())
            }
        }
    }

    private fun StringBuilder.appendVersionReport(
        rootProject: Project,
        mcVersion: String,
        versionConfig: VersionConfiguration,
        moduleConfig: ModuleConfiguration?,
        publishingConfig: PublishingConfiguration,
    ) {
        appendLine("version: $mcVersion")
        appendLine("java: ${versionConfig.resolvedJavaVersion}")
        appendLine("loaders: ${versionConfig.loaders.joinToString { it.loaderName }}")
        appendLine("commonRawHooks: ${versionConfig.rawCommonProjectActions.size}")

        for (loaderConfig in versionConfig.loaders) {
            val project = findLoaderProject(rootProject, mcVersion, loaderConfig, moduleConfig)
            appendLine("  loader: ${loaderConfig.loaderName}")
            appendLine("  project: ${project?.path ?: "missing"}")
            appendLine("  underlying: ${underlyingPlugin(loaderConfig)}")
            appendLine("  mappings: ${mappingMode(versionConfig, loaderConfig)}")
            appendLine("  mixins: ${describeMixins(loaderConfig)}")
            appendLine("  publishTask: ${project?.let { PublishingConfigurator.selectPublishTask(it, loaderConfig, publishingConfig)?.name } ?: "n/a"}")
            appendLine("  modConfigs: ${project?.configurations?.names?.filter { it.startsWith("mod") }?.sorted()?.joinToString().orEmpty()}")
        }

        appendLine()
    }

    private fun findLoaderProject(
        rootProject: Project,
        mcVersion: String,
        loaderConfig: LoaderConfiguration,
        moduleConfig: ModuleConfiguration?,
    ): Project? {
        return if (moduleConfig == null) {
            rootProject.findProject(":$mcVersion:${loaderConfig.loaderName}")
                ?: rootProject.findProject(":$mcVersion")
        } else {
            rootProject.findProject(":${moduleConfig.moduleName}:$mcVersion:${loaderConfig.loaderName}")
                ?: rootProject.findProject(":${moduleConfig.moduleName}:$mcVersion")
        }
    }

    private fun underlyingPlugin(loaderConfig: LoaderConfiguration) = when (loaderConfig) {
        is FabricConfiguration -> "fabric-loom"
        is ForgeConfiguration -> "net.neoforged.moddev.legacyforge"
        is LexForgeConfiguration -> "net.minecraftforge.gradle"
        is NeoForgeConfiguration -> "net.neoforged.moddev"
        is LegacyForgeConfiguration -> "com.gtnewhorizons.retrofuturagradle"
        else -> "unknown"
    }

    private fun mappingMode(versionConfig: VersionConfiguration, loaderConfig: LoaderConfiguration) = when (loaderConfig) {
        is FabricConfiguration -> if (versionConfig.minecraftVersion.startsWith("26.") || versionConfig.minecraftVersion.startsWith("27.")) {
            "unobfuscated"
        } else {
            "named dev / intermediary production"
        }
        is ForgeConfiguration -> "official dev / srg production"
        is LexForgeConfiguration -> {
            val channel = loaderConfig.mappingsChannel
                ?: if (versionConfig.parchmentMappingsVersion != null) "parchment" else "official"
            "fg7 $channel"
        }
        is NeoForgeConfiguration -> "neoform named dev"
        is LegacyForgeConfiguration -> "mcp"
        else -> "unknown"
    }

    private fun describeMixins(loaderConfig: LoaderConfiguration): String {
        val options: MixinOptions? = when (loaderConfig) {
            is FabricConfiguration -> loaderConfig.mixinOptions
            is ForgeConfiguration -> loaderConfig.mixinOptions
            is LexForgeConfiguration -> loaderConfig.mixinOptions
            is NeoForgeConfiguration -> loaderConfig.mixinOptions
            else -> null
        }

        if (options == null) return "n/a"

        return "autoDetect=${options.autoDetect}, explicit=${options.explicitConfigs}, refmap=${options.refmapName ?: "default"}"
    }
}
