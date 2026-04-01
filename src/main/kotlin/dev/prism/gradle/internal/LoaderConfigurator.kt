package dev.prism.gradle.internal

import dev.prism.gradle.dsl.FabricConfiguration
import dev.prism.gradle.dsl.ForgeConfiguration
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
    ) {
        when (loaderConfig) {
            is FabricConfiguration -> FabricConfigurator.configure(
                loaderProject, commonProject, versionConfig, loaderConfig, metadata, extraRepositories
            )
            is ForgeConfiguration -> ForgeConfigurator.configure(
                loaderProject, commonProject, versionConfig, loaderConfig, metadata, extraRepositories
            )
            is NeoForgeConfiguration -> NeoForgeConfigurator.configure(
                loaderProject, commonProject, versionConfig, loaderConfig, metadata, extraRepositories
            )
        }
    }
}
