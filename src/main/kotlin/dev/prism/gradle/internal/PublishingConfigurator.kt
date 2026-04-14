package dev.prism.gradle.internal

import dev.prism.gradle.dsl.FabricConfiguration
import dev.prism.gradle.dsl.ForgeConfiguration
import dev.prism.gradle.dsl.LexForgeConfiguration
import dev.prism.gradle.dsl.LegacyForgeConfiguration
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
import org.gradle.api.Task
import java.io.File

object PublishingConfigurator {
    private val PLATFORM_TASKS = listOf("publishCurseforge", "publishModrinth", "publishMods")

    internal fun defaultPublishTaskName(loaderConfig: LoaderConfiguration): String = when (loaderConfig) {
        is FabricConfiguration -> "remapJar"
        is LegacyForgeConfiguration -> "reobfJar"
        is ForgeConfiguration -> "jar"
        is LexForgeConfiguration -> "jar"
        is NeoForgeConfiguration -> "jar"
        else -> "jar"
    }

    internal fun selectPublishTask(
        project: Project,
        loaderConfig: LoaderConfiguration,
        publishingConfig: PublishingConfiguration? = null,
    ): Task? {
        publishingConfig?.artifactTaskName?.let {
            return project.tasks.findByName(it)
        }
        if (loaderConfig !is FabricConfiguration) {
            project.tasks.findByName("reobfShadowJar")?.let { return it }
            project.tasks.findByName("shadowJar")?.let { return it }
        }
        return project.tasks.findByName(defaultPublishTaskName(loaderConfig))
            ?: project.tasks.findByName("jar")
    }

    internal fun resolvePublishFile(
        project: Project,
        loaderConfig: LoaderConfiguration,
        publishingConfig: PublishingConfiguration,
    ): File? {
        publishingConfig.artifactPath?.let { return project.rootProject.file(it) }
        return selectPublishTask(project, loaderConfig, publishingConfig)?.outputs?.files?.singleFile
    }

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
            is LexForgeConfiguration -> loaderConfig.pubDeps.deps
            is NeoForgeConfiguration -> loaderConfig.pubDeps.deps
            else -> emptyList()
        }

        val allDeps = publishingConfig.pubDeps.deps + versionConfig.pubDeps.deps + loaderPubDeps

        loaderProject.afterEvaluate { proj ->
            val publishMods = proj.extensions.getByType(ModPublishExtension::class.java)
            wirePublishTaskDependencies(proj, loaderConfig, publishingConfig)

            val jarFile = resolvePublishFile(proj, loaderConfig, publishingConfig)
            if (jarFile != null) {
                publishMods.file.set(jarFile)

                val name = publishingConfig.displayName ?: jarFile.name
                publishMods.displayName.set(name)
            } else {
                proj.logger.warn("Prism: No publishable artifact found for ${proj.path}")
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

            publishMods.modLoaders.add(loaderConfig.publishLoaderSlug)

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

    private fun wirePublishTaskDependencies(
        project: Project,
        loaderConfig: LoaderConfiguration,
        publishingConfig: PublishingConfiguration,
    ) {
        val cleanTask = project.tasks.findByName("clean")
        val publishArtifactTask = selectPublishTask(project, loaderConfig, publishingConfig)

        if (cleanTask != null && publishArtifactTask != null) {
            publishArtifactTask.mustRunAfter(cleanTask)
        }

        project.tasks.matching { it.name in PLATFORM_TASKS }.configureEach { publishTask ->
            if (cleanTask != null) {
                publishTask.dependsOn(cleanTask)
            }
            if (publishArtifactTask != null) {
                publishTask.dependsOn(publishArtifactTask)
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

    fun createAggregateTask(project: Project, excludeChildren: Set<String> = emptySet()) {
        if (project.tasks.findByName("publishAllMods") != null) return

        project.tasks.register("publishAllMods") { task ->
            task.group = "publishing"
            task.description = "Publishes all mod loader JARs to configured platforms"
        }

        for (taskName in PLATFORM_TASKS) {
            if (project.tasks.findByName(taskName) == null) {
                project.tasks.register(taskName) { task ->
                    task.group = "publishing"
                }
            }
        }

        project.childProjects.forEach { (name, child) ->
            if (name !in excludeChildren) {
                wirePublishTasks(project, child)
            }
        }
    }

    private fun wirePublishTasks(aggregateProject: Project, child: Project) {
        child.tasks.matching { it.name == "publishMods" }.configureEach { publishTask ->
            aggregateProject.tasks.named("publishAllMods").configure { it.dependsOn(publishTask) }
        }
        for (taskName in PLATFORM_TASKS) {
            child.tasks.matching { it.name == taskName }.configureEach { platformTask ->
                aggregateProject.tasks.named(taskName).configure { it.dependsOn(platformTask) }
            }
        }
        child.childProjects.values.forEach { grandchild ->
            wirePublishTasks(aggregateProject, grandchild)
        }
    }
}
