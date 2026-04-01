package dev.prism.gradle.internal

import dev.prism.gradle.dsl.LoaderConfiguration
import dev.prism.gradle.dsl.MetadataExtension
import dev.prism.gradle.dsl.VersionConfiguration
import org.gradle.api.Project
import org.gradle.api.plugins.BasePluginExtension

object JarNaming {
    fun configure(
        project: Project,
        metadata: MetadataExtension,
        versionConfig: VersionConfiguration,
        loaderConfig: LoaderConfiguration,
    ) {
        project.extensions.configure(BasePluginExtension::class.java) { base ->
            base.archivesName.set(
                "${metadata.modId}-${versionConfig.minecraftVersion}-${loaderConfig.loaderDisplayName}"
            )
        }

        val modVersion = metadata.version.ifEmpty { project.rootProject.version.toString() }
        project.version = modVersion
    }
}
