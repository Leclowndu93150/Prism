package dev.prism.gradle

import dev.prism.gradle.dsl.FabricConfiguration
import dev.prism.gradle.dsl.ForgeConfiguration
import dev.prism.gradle.dsl.NeoForgeConfiguration
import dev.prism.gradle.dsl.PrismExtension
import dev.prism.gradle.internal.CommonConfigurator
import dev.prism.gradle.internal.DependencyConfigurator
import dev.prism.gradle.internal.KotlinConfigurator
import dev.prism.gradle.internal.LoaderConfigurator
import dev.prism.gradle.internal.PublishingConfigurator
import dev.prism.gradle.internal.SharedCommonConfigurator
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

        val sharedProject = rootProject.findProject(":common")
        val hasSharedCommon = sharedProject != null

        if (hasSharedCommon) {
            val minJava = extension.versions.values.minOfOrNull { it.resolvedJavaVersion } ?: 21
            SharedCommonConfigurator.configure(sharedProject!!, extension.metadata, extension.extraRepositories, minJava)

            if (extension.versions.values.any { it.kotlinVersion != null }) {
                val kotlinVersion = extension.versions.values.mapNotNull { it.kotlinVersion }.first()
                KotlinConfigurator.apply(sharedProject, extension.versions.values.first { it.kotlinVersion != null })
            }
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
            KotlinConfigurator.apply(commonProject, versionConfig)
            DependencyConfigurator.apply(commonProject, versionConfig.commonDeps)

            if (hasSharedCommon) {
                SharedCommonConfigurator.wireInto(commonProject, sharedProject!!)
            }

            for (loaderConfig in versionConfig.loaders) {
                val loaderProject = rootProject.findProject(":$mcVersion:${loaderConfig.loaderName}")
                    ?: throw IllegalStateException(
                        "Loader project :$mcVersion:${loaderConfig.loaderName} not found. " +
                        "Make sure you declared it in settings.gradle.kts."
                    )

                LoaderConfigurator.configure(
                    loaderProject, commonProject, versionConfig, loaderConfig, extension.metadata, extension.extraRepositories,
                    if (hasSharedCommon) sharedProject else null
                )

                KotlinConfigurator.apply(loaderProject, versionConfig)

                val isFabric = loaderConfig is FabricConfiguration
                val deps = when (loaderConfig) {
                    is FabricConfiguration -> loaderConfig.deps
                    is ForgeConfiguration -> loaderConfig.deps
                    is NeoForgeConfiguration -> loaderConfig.deps
                    else -> null
                }
                if (deps != null) {
                    DependencyConfigurator.apply(loaderProject, deps, isFabric)
                }

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
