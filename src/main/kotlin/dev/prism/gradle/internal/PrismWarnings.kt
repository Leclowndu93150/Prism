package dev.prism.gradle.internal

import dev.prism.gradle.dsl.FabricConfiguration
import dev.prism.gradle.dsl.ForgeConfiguration
import dev.prism.gradle.dsl.LexForgeConfiguration
import dev.prism.gradle.dsl.LegacyForgeConfiguration
import dev.prism.gradle.dsl.LoaderConfiguration
import dev.prism.gradle.dsl.MixinOptions
import dev.prism.gradle.dsl.NeoForgeConfiguration
import dev.prism.gradle.dsl.PublishingConfiguration
import dev.prism.gradle.dsl.VersionConfiguration
import org.gradle.api.Project

object PrismWarnings {
    fun reportLoaderWarnings(project: Project, loaderConfig: LoaderConfiguration, publishingConfig: PublishingConfiguration?) {
        val mixins = when (loaderConfig) {
            is FabricConfiguration -> loaderConfig.mixinOptions
            is ForgeConfiguration -> loaderConfig.mixinOptions
            is LexForgeConfiguration -> loaderConfig.mixinOptions
            is NeoForgeConfiguration -> loaderConfig.mixinOptions
            else -> null
        }

        warnOnEmptyExplicitMixins(project, loaderConfig, mixins)

        if (publishingConfig != null && publishingConfig.isConfigured) {
            val taskName = PublishingConfigurator.selectPublishTaskName(project, loaderConfig, publishingConfig)
            if (taskName == null && publishingConfig.artifactPath == null) {
                project.logger.warn("Prism: No publish artifact task found for ${project.path}. Configure publishing.artifactTask() or artifactFile() if needed.")
            }
        }
    }

    fun reportVersionLoaderMismatches(project: Project, mcVersion: String, versionConfig: VersionConfiguration) {
        val atLeast1211 = compareMcVersion(mcVersion, "1.21.1") >= 0

        if (versionConfig.forgeConfig != null && atLeast1211) {
            project.logger.warn(
                "Prism: version '$mcVersion' uses forge { } (MDG Legacy) but $mcVersion >= 1.21.1. " +
                "Use lexForge { } for Forge 1.21.1+."
            )
        }
        if (versionConfig.lexForgeConfig != null && !atLeast1211) {
            project.logger.warn(
                "Prism: version '$mcVersion' uses lexForge { } (FG7) but $mcVersion < 1.21.1. " +
                "Use forge { } for Forge versions below 1.21.1."
            )
        }
    }

    private fun compareMcVersion(a: String, b: String): Int {
        val aParts = a.split(".").mapNotNull { it.toIntOrNull() }
        val bParts = b.split(".").mapNotNull { it.toIntOrNull() }
        val len = maxOf(aParts.size, bParts.size)
        for (i in 0 until len) {
            val av = aParts.getOrElse(i) { 0 }
            val bv = bParts.getOrElse(i) { 0 }
            if (av != bv) return av.compareTo(bv)
        }
        return 0
    }

    private fun warnOnEmptyExplicitMixins(project: Project, loaderConfig: LoaderConfiguration, mixins: MixinOptions?) {
        if (mixins != null && !mixins.autoDetect && mixins.explicitConfigs.isEmpty()) {
            project.logger.warn("Prism: ${project.path} disabled mixin auto-detect for ${loaderConfig.loaderName} but did not declare any mixin configs.")
        }
    }
}
