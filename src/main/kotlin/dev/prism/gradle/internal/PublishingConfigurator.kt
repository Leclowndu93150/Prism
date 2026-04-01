package dev.prism.gradle.internal

import dev.prism.gradle.dsl.FabricConfiguration
import dev.prism.gradle.dsl.ForgeConfiguration
import dev.prism.gradle.dsl.LoaderConfiguration
import dev.prism.gradle.dsl.MetadataExtension
import dev.prism.gradle.dsl.NeoForgeConfiguration
import dev.prism.gradle.dsl.PublishingConfiguration
import dev.prism.gradle.dsl.PublishingDep
import dev.prism.gradle.dsl.PublishingDepType
import dev.prism.gradle.dsl.PublishingPlatform
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

        val loaderPubDeps = when (loaderConfig) {
            is FabricConfiguration -> loaderConfig.pubDeps.deps
            is ForgeConfiguration -> loaderConfig.pubDeps.deps
            is NeoForgeConfiguration -> loaderConfig.pubDeps.deps
            else -> emptyList()
        }

        val allDeps = publishingConfig.pubDeps.deps + versionConfig.pubDeps.deps + loaderPubDeps

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

                    for (dep in allDeps) {
                        if (dep.platform == PublishingPlatform.MODRINTH) continue
                        applyDepToCurseforge(curseforge, dep)
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

                    for (dep in allDeps) {
                        if (dep.platform == PublishingPlatform.CURSEFORGE) continue
                        applyDepToModrinth(modrinth, dep)
                    }
                }
            }
        }
    }

    private fun applyDepToCurseforge(curseforge: Any, dep: PublishingDep) {
        val methodName = when (dep.type) {
            PublishingDepType.REQUIRED -> "requires"
            PublishingDepType.OPTIONAL -> "optional"
            PublishingDepType.INCOMPATIBLE -> "incompatible"
            PublishingDepType.EMBEDDED -> "embeds"
        }
        try {
            val method = curseforge.javaClass.methods.first {
                it.name == methodName && it.parameterCount == 1 && it.parameterTypes[0] == String::class.java
            }
            method.invoke(curseforge, dep.slug)
        } catch (_: Exception) {
        }
    }

    private fun applyDepToModrinth(modrinth: Any, dep: PublishingDep) {
        val methodName = when (dep.type) {
            PublishingDepType.REQUIRED -> "requires"
            PublishingDepType.OPTIONAL -> "optional"
            PublishingDepType.INCOMPATIBLE -> "incompatible"
            PublishingDepType.EMBEDDED -> "embeds"
        }
        try {
            val method = modrinth.javaClass.methods.first {
                it.name == methodName && it.parameterCount == 1 && it.parameterTypes[0] == String::class.java
            }
            method.invoke(modrinth, dep.slug)
        } catch (_: Exception) {
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
