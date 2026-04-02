package dev.prism.gradle

import dev.prism.gradle.dsl.FabricConfiguration
import dev.prism.gradle.dsl.ForgeConfiguration
import dev.prism.gradle.dsl.NeoForgeConfiguration
import dev.prism.gradle.dsl.PrismExtension
import dev.prism.gradle.internal.CommonConfigurator
import dev.prism.gradle.internal.DependencyConfigurator
import dev.prism.gradle.internal.KotlinConfigurator
import dev.prism.gradle.internal.LoaderConfigurator
import dev.prism.gradle.internal.MavenPublishConfigurator
import dev.prism.gradle.internal.PublishingConfigurator
import dev.prism.gradle.internal.SharedCommonConfigurator
import dev.prism.gradle.internal.Validation
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
        if (extension.metadata.version.isEmpty()) {
            extension.metadata.version = rootProject.version.toString()
        }

        if (extension.metadata.group.isEmpty()) {
            extension.metadata.group = rootProject.group.toString()
        }

        Validation.validate(extension)

        val sharedProject = rootProject.findProject(":common")
        val hasSharedCommon = sharedProject != null

        if (hasSharedCommon) {
            val minJava = extension.versions.values.minOfOrNull { it.resolvedJavaVersion } ?: 21
            SharedCommonConfigurator.configure(sharedProject!!, extension.metadata, extension.extraRepositories, minJava)

            if (extension.versions.values.any { it.kotlinVersion != null }) {
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

            val isSingleLoader = versionConfig.loaders.size == 1 && rootProject.findProject(":$mcVersion:common") == null

            if (isSingleLoader) {
                configureSingleLoader(rootProject, mcVersion, versionConfig, extension, sharedProject, hasSharedCommon)
            } else {
                configureMultiLoader(rootProject, mcVersion, versionConfig, extension, sharedProject, hasSharedCommon)
            }
        }

        if (extension.publishingConfig.isConfigured) {
            PublishingConfigurator.createAggregateTask(rootProject)
        }

        if (extension.publishingConfig.hasMaven) {
            MavenPublishConfigurator.createAggregateTask(rootProject)
        }
    }

    private fun configureSingleLoader(
        rootProject: Project,
        mcVersion: String,
        versionConfig: dev.prism.gradle.dsl.VersionConfiguration,
        extension: PrismExtension,
        sharedProject: Project?,
        hasSharedCommon: Boolean,
    ) {
        val loaderConfig = versionConfig.loaders.first()
        val loaderProject = rootProject.findProject(":$mcVersion")
            ?: throw IllegalStateException(
                "Prism: Project ':$mcVersion' not found for single-loader ${loaderConfig.loaderDisplayName}.\n" +
                "Make sure settings.gradle.kts has:\n" +
                "  version(\"$mcVersion\") {\n" +
                "      ${loaderConfig.loaderName}()\n" +
                "  }\n" +
                "And the directory exists: versions/$mcVersion/"
            )

        LoaderConfigurator.configureSingle(
            loaderProject, versionConfig, loaderConfig, extension.metadata, extension.extraRepositories,
            if (hasSharedCommon) sharedProject else null
        )

        KotlinConfigurator.apply(loaderProject, versionConfig)

        DependencyConfigurator.apply(loaderProject, versionConfig.commonDeps)

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

        if (hasSharedCommon) {
            SharedCommonConfigurator.wireInto(loaderProject, sharedProject!!)
        }

        if (extension.publishingConfig.isConfigured) {
            PublishingConfigurator.configure(
                loaderProject, versionConfig, loaderConfig,
                extension.metadata, extension.publishingConfig
            )
        }

        if (extension.publishingConfig.hasMaven) {
            MavenPublishConfigurator.configure(
                loaderProject, versionConfig, loaderConfig,
                extension.metadata, extension.publishingConfig.mavenRepos
            )
        }
    }

    private fun configureMultiLoader(
        rootProject: Project,
        mcVersion: String,
        versionConfig: dev.prism.gradle.dsl.VersionConfiguration,
        extension: PrismExtension,
        sharedProject: Project?,
        hasSharedCommon: Boolean,
    ) {
        val commonProject = rootProject.findProject(":$mcVersion:common")
            ?: throw IllegalStateException(
                "Prism: Common project ':$mcVersion:common' not found.\n" +
                "For multi-loader, settings.gradle.kts needs:\n" +
                "  version(\"$mcVersion\") {\n" +
                "      common()\n" +
                "      fabric()\n" +
                "      neoforge()\n" +
                "  }\n" +
                "Or for single-loader, remove common() and keep only one loader."
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
                    "Prism: Loader project ':$mcVersion:${loaderConfig.loaderName}' not found.\n" +
                    "Add ${loaderConfig.loaderName}() to settings.gradle.kts:\n" +
                    "  version(\"$mcVersion\") {\n" +
                    "      common()\n" +
                    "      ${loaderConfig.loaderName}()\n" +
                    "  }\n" +
                    "And create: versions/$mcVersion/${loaderConfig.loaderName}/"
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

            if (extension.publishingConfig.hasMaven) {
                MavenPublishConfigurator.configure(
                    loaderProject, versionConfig, loaderConfig,
                    extension.metadata, extension.publishingConfig.mavenRepos
                )
            }
        }
    }
}
