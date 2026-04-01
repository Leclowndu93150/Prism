package dev.prism.gradle

import dev.prism.gradle.dsl.PrismExtension
import dev.prism.gradle.internal.CommonConfigurator
import dev.prism.gradle.internal.LoaderConfigurator
import dev.prism.gradle.internal.PublishingConfigurator
import org.gradle.api.Plugin
import org.gradle.api.Project

class PrismProjectPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        require(project == project.rootProject) {
            "The dev.prism plugin must only be applied to the root project."
        }

        val extension = PrismExtension(project)
        project.extensions.add("prism", extension)

        project.afterEvaluate { rootProject ->
            configureSubprojects(rootProject, extension)
        }
    }

    private fun configureSubprojects(rootProject: Project, extension: PrismExtension) {
        if (extension.metadata.modId.isEmpty()) {
            throw IllegalStateException("prism.metadata.modId must be set.")
        }

        if (extension.metadata.version.isEmpty()) {
            extension.metadata.version = rootProject.version.toString()
        }

        if (extension.metadata.group.isEmpty()) {
            extension.metadata.group = rootProject.group.toString()
        }

        for ((mcVersion, versionConfig) in extension.versions) {
            if (versionConfig.loaders.isEmpty()) {
                rootProject.logger.warn("Prism: version '$mcVersion' has no loaders configured, skipping.")
                continue
            }

            if (versionConfig.parchmentMinecraftVersion != null && versionConfig.parchmentMappingsVersion == null) {
                rootProject.logger.warn("Prism: version '$mcVersion' has parchmentMinecraftVersion set but parchmentMappingsVersion is missing. Parchment will not be applied.")
            }

            val commonProject = rootProject.findProject(":$mcVersion:common")
                ?: throw IllegalStateException(
                    "Common project :$mcVersion:common not found. " +
                    "Make sure you declared it in settings.gradle.kts with: " +
                    "prism { version(\"$mcVersion\") { common() } }"
                )

            CommonConfigurator.configure(commonProject, versionConfig, extension.metadata, extension.extraRepositories)

            for (loaderConfig in versionConfig.loaders) {
                val loaderProject = rootProject.findProject(":$mcVersion:${loaderConfig.loaderName}")
                    ?: throw IllegalStateException(
                        "Loader project :$mcVersion:${loaderConfig.loaderName} not found. " +
                        "Make sure you declared it in settings.gradle.kts."
                    )

                LoaderConfigurator.configure(
                    loaderProject, commonProject, versionConfig, loaderConfig, extension.metadata, extension.extraRepositories
                )

                if (extension.publishingConfig.isConfigured) {
                    PublishingConfigurator.configure(
                        loaderProject, versionConfig, loaderConfig,
                        extension.metadata, extension.publishingConfig
                    )
                }
            }
        }

        if (extension.publishingConfig.isConfigured) {
            PublishingConfigurator.createAggregateTask(rootProject)
        }
    }
}
