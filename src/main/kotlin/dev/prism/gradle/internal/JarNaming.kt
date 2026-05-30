package dev.prism.gradle.internal

import dev.prism.gradle.dsl.LoaderConfiguration
import dev.prism.gradle.dsl.MetadataExtension
import dev.prism.gradle.dsl.VersionConfiguration
import org.gradle.api.Project
import org.gradle.api.plugins.BasePluginExtension
import org.gradle.jvm.tasks.Jar

object JarNaming {
    fun resolve(
        metadata: MetadataExtension,
        versionConfig: VersionConfiguration,
        loaderConfig: LoaderConfiguration,
        modVersion: String,
    ): String {
        val template = metadata.archivesName
        if (template.isEmpty()) {
            return "${metadata.modId}-${versionConfig.minecraftVersion}-${loaderConfig.loaderDisplayName}"
        }
        return template
            .replace("{modId}", metadata.modId)
            .replace("{mc}", versionConfig.minecraftVersion)
            .replace("{loader}", loaderConfig.loaderDisplayName)
            .replace("{version}", modVersion)
    }

    fun configure(
        project: Project,
        metadata: MetadataExtension,
        versionConfig: VersionConfiguration,
        loaderConfig: LoaderConfiguration,
    ) {
        val modVersion = metadata.version.ifEmpty { project.rootProject.version.toString() }
        val archivesName = resolve(metadata, versionConfig, loaderConfig, modVersion)
        project.extensions.configure(BasePluginExtension::class.java) { base ->
            base.archivesName.set(archivesName)
        }
        project.version = modVersion

        if (metadata.includedFiles.isNotEmpty()) {
            project.tasks.named("jar", Jar::class.java) { jar ->
                for (included in metadata.includedFiles) {
                    jar.from(project.rootProject.file(included.path)) { spec ->
                        included.rename?.let { r ->
                            spec.rename { original ->
                                r.replace("{name}", original).replace("{archivesName}", archivesName)
                            }
                        }
                    }
                }
            }
        }
    }
}
