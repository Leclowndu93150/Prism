package dev.prism.gradle.internal

import dev.prism.gradle.dsl.FabricConfiguration
import dev.prism.gradle.dsl.ForgeConfiguration
import dev.prism.gradle.dsl.LegacyForgeConfiguration
import dev.prism.gradle.dsl.LoaderConfiguration
import dev.prism.gradle.dsl.MixinOptions
import dev.prism.gradle.dsl.NeoForgeConfiguration
import dev.prism.gradle.dsl.PublishingConfiguration
import org.gradle.api.Project

object PrismWarnings {
    fun reportLoaderWarnings(project: Project, loaderConfig: LoaderConfiguration, publishingConfig: PublishingConfiguration?) {
        val mixins = when (loaderConfig) {
            is FabricConfiguration -> loaderConfig.mixinOptions
            is ForgeConfiguration -> loaderConfig.mixinOptions
            is NeoForgeConfiguration -> loaderConfig.mixinOptions
            else -> null
        }

        warnOnEmptyExplicitMixins(project, loaderConfig, mixins)

        if (publishingConfig != null && publishingConfig.isConfigured) {
            val task = PublishingConfigurator.selectPublishTask(project, publishingConfig)
            if (task == null) {
                project.logger.warn("Prism: No publish artifact task found for ${project.path}. Configure publishing.artifactTask() or artifactFile() if needed.")
            } else if (loaderConfig is ForgeConfiguration && task.name == "jar") {
                project.logger.warn("Prism: ${project.path} is publishing the plain jar. Expected reobfJar for Forge unless explicitly overridden.")
            }
        }
    }

    private fun warnOnEmptyExplicitMixins(project: Project, loaderConfig: LoaderConfiguration, mixins: MixinOptions?) {
        if (mixins != null && !mixins.autoDetect && mixins.explicitConfigs.isEmpty()) {
            project.logger.warn("Prism: ${project.path} disabled mixin auto-detect for ${loaderConfig.loaderName} but did not declare any mixin configs.")
        }
    }
}
