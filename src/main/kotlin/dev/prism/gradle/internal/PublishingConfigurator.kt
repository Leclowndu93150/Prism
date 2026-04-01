package dev.prism.gradle.internal

import dev.prism.gradle.dsl.LoaderConfiguration
import dev.prism.gradle.dsl.MetadataExtension
import dev.prism.gradle.dsl.PublishingConfiguration
import dev.prism.gradle.dsl.ReleaseType
import dev.prism.gradle.dsl.VersionConfiguration
import me.modmuss50.mpp.ModPublishExtension
import org.gradle.api.Project

object PublishingConfigurator {
    fun configure(
        loaderProject: Project,
        versionConfig: VersionConfiguration,
        loaderConfig: LoaderConfiguration,
        metadata: MetadataExtension,
        publishingConfig: PublishingConfiguration,
    ) {
        if (!publishingConfig.isConfigured) return

        loaderProject.pluginManager.apply("me.modmuss50.mod-publish-plugin")

        loaderProject.afterEvaluate { proj ->
            val publishMods = proj.extensions.getByType(ModPublishExtension::class.java)

            val jarTask = proj.tasks.findByName("remapJar") ?: proj.tasks.findByName("jar")
            if (jarTask != null) {
                publishMods.file.set(jarTask.outputs.files.singleFile)
            }

            val changelog = publishingConfig.changelog
                ?: publishingConfig.changelogFile?.let { proj.rootProject.file(it).readText() }
                ?: ""
            publishMods.changelog.set(changelog)

            val modVersion = metadata.version.ifEmpty { proj.rootProject.version.toString() }
            publishMods.version.set(modVersion)

            publishMods.type.set(
                when (publishingConfig.type) {
                    ReleaseType.STABLE -> publishMods.STABLE
                    ReleaseType.BETA -> publishMods.BETA
                    ReleaseType.ALPHA -> publishMods.ALPHA
                }
            )

            publishMods.modLoaders.add(loaderConfig.loaderName)

            val mcVersions = versionConfig.minecraftVersionRange ?: listOf(versionConfig.minecraftVersion)

            publishingConfig.curseforgeConfig?.let { cf ->
                publishMods.curseforge { curseforge ->
                    curseforge.projectId.set(cf.projectId)
                    cf.accessToken?.let { token ->
                        curseforge.accessToken.set(token)
                    }
                    for (v in mcVersions) {
                        curseforge.minecraftVersions.add(v)
                    }
                }
            }

            publishingConfig.modrinthConfig?.let { mr ->
                publishMods.modrinth { modrinth ->
                    modrinth.projectId.set(mr.projectId)
                    mr.accessToken?.let { token ->
                        modrinth.accessToken.set(token)
                    }
                    for (v in mcVersions) {
                        modrinth.minecraftVersions.add(v)
                    }
                }
            }
        }
    }

    fun createAggregateTask(rootProject: Project) {
        rootProject.tasks.register("publishAllMods") { task ->
            task.group = "publishing"
            task.description = "Publishes all mod loader JARs to configured platforms"

            rootProject.subprojects.forEach { sub ->
                sub.tasks.matching { it.name == "publishMods" }.configureEach {
                    task.dependsOn(it)
                }
            }
        }
    }
}
