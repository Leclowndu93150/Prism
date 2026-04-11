package dev.prism.gradle.internal

import dev.prism.gradle.dsl.FabricConfiguration
import dev.prism.gradle.dsl.ForgeConfiguration
import dev.prism.gradle.dsl.LexForgeConfiguration
import dev.prism.gradle.dsl.LegacyForgeConfiguration
import dev.prism.gradle.dsl.LoaderConfiguration
import dev.prism.gradle.dsl.MetadataExtension
import dev.prism.gradle.dsl.NeoForgeConfiguration
import dev.prism.gradle.dsl.RepositoryEntry
import dev.prism.gradle.dsl.VersionConfiguration
import org.gradle.api.Project

object LoaderConfigurator {
    fun configure(
        loaderProject: Project,
        commonProject: Project,
        versionConfig: VersionConfiguration,
        loaderConfig: LoaderConfiguration,
        metadata: MetadataExtension,
        extraRepositories: List<RepositoryEntry> = emptyList(),
        sharedProject: Project? = null,
    ) {
        when (loaderConfig) {
            is FabricConfiguration -> FabricConfigurator.configure(
                loaderProject, commonProject, versionConfig, loaderConfig, metadata, extraRepositories, sharedProject
            )
            is ForgeConfiguration -> ForgeConfigurator.configure(
                loaderProject, commonProject, versionConfig, loaderConfig, metadata, extraRepositories, sharedProject
            )
            is LexForgeConfiguration -> LexForgeConfigurator.configure(
                loaderProject, commonProject, versionConfig, loaderConfig, metadata, extraRepositories, sharedProject
            )
            is NeoForgeConfiguration -> NeoForgeConfigurator.configure(
                loaderProject, commonProject, versionConfig, loaderConfig, metadata, extraRepositories, sharedProject
            )
            is LegacyForgeConfiguration -> LegacyForgeConfigurator.configure(
                loaderProject, commonProject, versionConfig, loaderConfig, metadata, extraRepositories, sharedProject
            )
        }
    }

    fun configureSingle(
        project: Project,
        versionConfig: VersionConfiguration,
        loaderConfig: LoaderConfiguration,
        metadata: MetadataExtension,
        extraRepositories: List<RepositoryEntry> = emptyList(),
        sharedProject: Project? = null,
    ) {
        when (loaderConfig) {
            is FabricConfiguration -> FabricConfigurator.configureSingle(
                project, versionConfig, loaderConfig, metadata, extraRepositories, sharedProject
            )
            is ForgeConfiguration -> ForgeConfigurator.configureSingle(
                project, versionConfig, loaderConfig, metadata, extraRepositories, sharedProject
            )
            is LexForgeConfiguration -> LexForgeConfigurator.configureSingle(
                project, versionConfig, loaderConfig, metadata, extraRepositories, sharedProject
            )
            is NeoForgeConfiguration -> NeoForgeConfigurator.configureSingle(
                project, versionConfig, loaderConfig, metadata, extraRepositories, sharedProject
            )
            is LegacyForgeConfiguration -> LegacyForgeConfigurator.configureSingle(
                project, versionConfig, loaderConfig, metadata, extraRepositories, sharedProject
            )
        }
    }
}
