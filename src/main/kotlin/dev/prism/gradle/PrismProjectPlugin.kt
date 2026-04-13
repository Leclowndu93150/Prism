package dev.prism.gradle

import dev.prism.gradle.dsl.FabricConfiguration
import dev.prism.gradle.dsl.ForgeConfiguration
import dev.prism.gradle.dsl.LexForgeConfiguration
import dev.prism.gradle.dsl.LegacyForgeConfiguration
import dev.prism.gradle.dsl.ModuleConfiguration
import dev.prism.gradle.dsl.NeoForgeConfiguration
import dev.prism.gradle.dsl.PrismExtension
import dev.prism.gradle.dsl.VersionConfiguration
import dev.prism.gradle.internal.CommonConfigurator
import dev.prism.gradle.internal.DependencyConfigurator
import dev.prism.gradle.internal.KotlinConfigurator
import dev.prism.gradle.internal.LoaderConfigurator
import dev.prism.gradle.internal.MavenPublishConfigurator
import dev.prism.gradle.internal.PublishingConfigurator
import dev.prism.gradle.internal.PrismDoctor
import dev.prism.gradle.internal.PrismWarnings
import dev.prism.gradle.internal.RepositorySetup
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

        if (extension.globalKotlinVersion != null) {
            for (versionConfig in extension.versions.values) {
                if (versionConfig.kotlinVersion == null) {
                    versionConfig.kotlinVersion = extension.globalKotlinVersion
                }
            }
        }

        Validation.validate(extension)

        val sharedProject = rootProject.findProject(":common")
        val hasSharedCommon = sharedProject != null

        if (hasSharedCommon) {
            val minJava = extension.versions.values.minOfOrNull { it.resolvedJavaVersion } ?: 21
            SharedCommonConfigurator.configure(sharedProject!!, extension.metadata, extension.extraRepositories, minJava, extension.sharedCommonConfig)
            for (action in extension.sharedCommonConfig.rawProjectActions) {
                action.execute(sharedProject)
            }

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

            PrismWarnings.reportVersionLoaderMismatches(rootProject, mcVersion, versionConfig)

            val isSingleLoader = versionConfig.loaders.size == 1 && rootProject.findProject(":$mcVersion:common") == null

            if (isSingleLoader) {
                configureSingleLoader(rootProject, mcVersion, versionConfig, extension, sharedProject, hasSharedCommon)
            } else {
                configureMultiLoader(rootProject, mcVersion, versionConfig, extension, sharedProject, hasSharedCommon)
            }
        }

        val moduleNames = extension.modules.keys.toSet()

        if (extension.publishingConfig.isConfigured) {
            PublishingConfigurator.createAggregateTask(rootProject, moduleNames)
        }

        if (extension.publishingConfig.hasMaven) {
            MavenPublishConfigurator.createAggregateTask(rootProject, moduleNames)
        }

        if (extension.modules.isNotEmpty()) {
            RepositorySetup.configure(rootProject, extension.extraRepositories)
        }

        for ((moduleName, moduleConfig) in extension.modules) {
            configureModule(rootProject, moduleName, moduleConfig, extension)
        }

        PrismDoctor.register(rootProject, extension)
    }

    private fun configureSingleLoader(
        rootProject: Project,
        mcVersion: String,
        versionConfig: VersionConfiguration,
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
        CommonConfigurator.applyDownstreamSupportDeps(loaderProject, versionConfig)

        val isFabric = loaderConfig is FabricConfiguration
        val deps = when (loaderConfig) {
            is FabricConfiguration -> loaderConfig.deps
            is ForgeConfiguration -> loaderConfig.deps
            is LexForgeConfiguration -> loaderConfig.deps
            is NeoForgeConfiguration -> loaderConfig.deps
            is LegacyForgeConfiguration -> loaderConfig.deps
            else -> null
        }
        if (deps != null) {
            DependencyConfigurator.apply(loaderProject, deps, isFabric)
        }

        if (hasSharedCommon) {
            SharedCommonConfigurator.applyDownstreamSupportDeps(loaderProject, extension.sharedCommonConfig)
            DependencyConfigurator.apply(loaderProject, extension.sharedCommonConfig.deps, isFabric, isSharedCommonDownstream = true)
            SharedCommonConfigurator.wireInto(loaderProject, sharedProject!!)
        }

        PrismWarnings.reportLoaderWarnings(loaderProject, loaderConfig, extension.publishingConfig)

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
        versionConfig: VersionConfiguration,
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
        for (action in versionConfig.rawCommonProjectActions) {
            action.execute(commonProject)
        }

        if (hasSharedCommon) {
            SharedCommonConfigurator.applyDownstreamSupportDeps(commonProject, extension.sharedCommonConfig)
            DependencyConfigurator.apply(commonProject, extension.sharedCommonConfig.deps)
            SharedCommonConfigurator.wireInto(commonProject, sharedProject!!)
        }

        if (extension.publishingConfig.hasMaven && extension.publishingConfig.publishCommonJar) {
            MavenPublishConfigurator.configureCommon(
                commonProject, versionConfig, extension.metadata, extension.publishingConfig.mavenRepos
            )
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
            DependencyConfigurator.apply(loaderProject, versionConfig.commonDeps, isFabric)
            CommonConfigurator.applyDownstreamSupportDeps(loaderProject, versionConfig)

            val deps = when (loaderConfig) {
                is FabricConfiguration -> loaderConfig.deps
                is ForgeConfiguration -> loaderConfig.deps
                is LexForgeConfiguration -> loaderConfig.deps
                is NeoForgeConfiguration -> loaderConfig.deps
                else -> null
            }
            if (deps != null) {
                DependencyConfigurator.apply(loaderProject, deps, isFabric)
            }

            if (hasSharedCommon) {
                SharedCommonConfigurator.applyDownstreamSupportDeps(loaderProject, extension.sharedCommonConfig)
                DependencyConfigurator.apply(loaderProject, extension.sharedCommonConfig.deps, isFabric, isSharedCommonDownstream = true)
            }

            PrismWarnings.reportLoaderWarnings(loaderProject, loaderConfig, extension.publishingConfig)

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

    private fun configureModule(
        rootProject: Project,
        moduleName: String,
        moduleConfig: ModuleConfiguration,
        extension: PrismExtension,
    ) {
        if (moduleConfig.metadata.version.isEmpty()) {
            moduleConfig.metadata.version = rootProject.version.toString()
        }
        if (moduleConfig.metadata.group.isEmpty()) {
            moduleConfig.metadata.group = rootProject.group.toString()
        }

        if (moduleConfig.kotlinVersion != null) {
            for (versionConfig in moduleConfig.versions.values) {
                if (versionConfig.kotlinVersion == null) {
                    versionConfig.kotlinVersion = moduleConfig.kotlinVersion
                }
            }
        }

        val moduleProject = rootProject.findProject(":$moduleName")
        if (moduleProject != null) {
            RepositorySetup.configure(moduleProject, extension.extraRepositories)
        }

        for ((mcVersion, versionConfig) in moduleConfig.versions) {
            if (versionConfig.loaders.isEmpty()) {
                rootProject.logger.warn("Prism: module '$moduleName' version '$mcVersion' has no loaders configured, skipping.")
                continue
            }

            val isSingleLoader = versionConfig.loaders.size == 1 && rootProject.findProject(":$moduleName:$mcVersion:common") == null

            if (isSingleLoader) {
                configureModuleSingleLoader(rootProject, moduleName, mcVersion, versionConfig, moduleConfig, extension)
            } else {
                configureModuleMultiLoader(rootProject, moduleName, mcVersion, versionConfig, moduleConfig, extension)
            }
        }

        if (moduleConfig.publishingConfig.isConfigured) {
            val moduleProject = rootProject.findProject(":$moduleName")
            if (moduleProject != null) {
                PublishingConfigurator.createAggregateTask(moduleProject)
            }
        }

        if (moduleConfig.publishingConfig.hasMaven) {
            val moduleProject = rootProject.findProject(":$moduleName")
            if (moduleProject != null) {
                MavenPublishConfigurator.createAggregateTask(moduleProject)
            }
        }
    }

    private fun configureModuleSingleLoader(
        rootProject: Project,
        moduleName: String,
        mcVersion: String,
        versionConfig: VersionConfiguration,
        moduleConfig: ModuleConfiguration,
        extension: PrismExtension,
    ) {
        val loaderConfig = versionConfig.loaders.first()
        val loaderProject = rootProject.findProject(":$moduleName:$mcVersion")
            ?: throw IllegalStateException(
                "Prism: Project ':$moduleName:$mcVersion' not found for module '$moduleName'."
            )

        RepositorySetup.configure(loaderProject, extension.extraRepositories)

        LoaderConfigurator.configureSingle(
            loaderProject, versionConfig, loaderConfig, moduleConfig.metadata, extension.extraRepositories
        )

        KotlinConfigurator.apply(loaderProject, versionConfig)
        DependencyConfigurator.apply(loaderProject, versionConfig.commonDeps)
        CommonConfigurator.applyDownstreamSupportDeps(loaderProject, versionConfig)

        val isFabric = loaderConfig is FabricConfiguration
        val deps = when (loaderConfig) {
            is FabricConfiguration -> loaderConfig.deps
            is ForgeConfiguration -> loaderConfig.deps
            is LexForgeConfiguration -> loaderConfig.deps
            is NeoForgeConfiguration -> loaderConfig.deps
            is LegacyForgeConfiguration -> loaderConfig.deps
            else -> null
        }
        if (deps != null) {
            DependencyConfigurator.apply(loaderProject, deps, isFabric)
        }

        PrismWarnings.reportLoaderWarnings(loaderProject, loaderConfig, moduleConfig.publishingConfig)

        if (moduleConfig.publishingConfig.isConfigured) {
            PublishingConfigurator.configure(
                loaderProject, versionConfig, loaderConfig,
                moduleConfig.metadata, moduleConfig.publishingConfig
            )
        }

        if (moduleConfig.publishingConfig.hasMaven) {
            MavenPublishConfigurator.configure(
                loaderProject, versionConfig, loaderConfig,
                moduleConfig.metadata, moduleConfig.publishingConfig.mavenRepos
            )
        }
    }

    private fun configureModuleMultiLoader(
        rootProject: Project,
        moduleName: String,
        mcVersion: String,
        versionConfig: VersionConfiguration,
        moduleConfig: ModuleConfiguration,
        extension: PrismExtension,
    ) {
        val commonProject = rootProject.findProject(":$moduleName:$mcVersion:common")
            ?: throw IllegalStateException(
                "Prism: Common project ':$moduleName:$mcVersion:common' not found for module '$moduleName'."
            )

        CommonConfigurator.configure(commonProject, versionConfig, moduleConfig.metadata, extension.extraRepositories)
        KotlinConfigurator.apply(commonProject, versionConfig)
        DependencyConfigurator.apply(commonProject, versionConfig.commonDeps)
        for (action in versionConfig.rawCommonProjectActions) {
            action.execute(commonProject)
        }

        if (moduleConfig.publishingConfig.hasMaven && moduleConfig.publishingConfig.publishCommonJar) {
            MavenPublishConfigurator.configureCommon(
                commonProject, versionConfig, moduleConfig.metadata, moduleConfig.publishingConfig.mavenRepos
            )
        }

        for (loaderConfig in versionConfig.loaders) {
            val loaderProject = rootProject.findProject(":$moduleName:$mcVersion:${loaderConfig.loaderName}")
                ?: throw IllegalStateException(
                    "Prism: Loader project ':$moduleName:$mcVersion:${loaderConfig.loaderName}' not found for module '$moduleName'."
                )

            LoaderConfigurator.configure(
                loaderProject, commonProject, versionConfig, loaderConfig, moduleConfig.metadata, extension.extraRepositories
            )

            KotlinConfigurator.apply(loaderProject, versionConfig)

            val isFabric = loaderConfig is FabricConfiguration
            DependencyConfigurator.apply(loaderProject, versionConfig.commonDeps, isFabric)
            CommonConfigurator.applyDownstreamSupportDeps(loaderProject, versionConfig)
            val deps = when (loaderConfig) {
                is FabricConfiguration -> loaderConfig.deps
                is ForgeConfiguration -> loaderConfig.deps
                is LexForgeConfiguration -> loaderConfig.deps
                is NeoForgeConfiguration -> loaderConfig.deps
                else -> null
            }
            if (deps != null) {
                DependencyConfigurator.apply(loaderProject, deps, isFabric)
            }

            PrismWarnings.reportLoaderWarnings(loaderProject, loaderConfig, moduleConfig.publishingConfig)

            if (moduleConfig.publishingConfig.isConfigured) {
                PublishingConfigurator.configure(
                    loaderProject, versionConfig, loaderConfig,
                    moduleConfig.metadata, moduleConfig.publishingConfig
                )
            }

            if (moduleConfig.publishingConfig.hasMaven) {
                MavenPublishConfigurator.configure(
                    loaderProject, versionConfig, loaderConfig,
                    moduleConfig.metadata, moduleConfig.publishingConfig.mavenRepos
                )
            }
        }
    }
}
