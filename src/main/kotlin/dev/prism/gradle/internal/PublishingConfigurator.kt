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
    private val LEAF_PLATFORM_TASKS = listOf("publishCurseforge", "publishModrinth", "publishMods")
    private val PRISM_AGGREGATE_TASKS = listOf("prismPublishCurseforge", "prismPublishModrinth", "prismPublishMods")
    private const val PRISM_PUBLISH_ALL = "prismPublishAll"

    private fun aggregateNameFor(leafTaskName: String): String = when (leafTaskName) {
        "publishCurseforge" -> "prismPublishCurseforge"
        "publishModrinth" -> "prismPublishModrinth"
        "publishMods" -> "prismPublishMods"
        else -> "prism${leafTaskName.replaceFirstChar { it.titlecase() }}"
    }

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

        val allDeps = run {
            val merged = publishingConfig.pubDeps.deps + versionConfig.pubDeps.deps + loaderPubDeps
            val perSlug = linkedMapOf<Pair<PublishingPlatform?, String>, PublishingDep>()
            for (dep in merged) {
                perSlug[dep.platform to dep.slug] = dep
            }
            perSlug.values.toList()
        }

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

        project.tasks.matching { it.name in LEAF_PLATFORM_TASKS }.configureEach { publishTask ->
            if (cleanTask != null) {
                publishTask.dependsOn(cleanTask)
            }
            if (publishArtifactTask != null) {
                publishTask.dependsOn(publishArtifactTask)
            }
            publishTask.group = null
        }
    }

    private fun applyDepToCurseforge(curseforge: Any, dep: PublishingDep) {
        invokeDepMethod(curseforge, dep)
    }

    private fun applyDepToModrinth(modrinth: Any, dep: PublishingDep) {
        invokeDepMethod(modrinth, dep)
    }

    private fun invokeDepMethod(target: Any, dep: PublishingDep) {
        val methodName = when (dep.type) {
            PublishingDepType.REQUIRED -> "requires"
            PublishingDepType.OPTIONAL -> "optional"
            PublishingDepType.INCOMPATIBLE -> "incompatible"
            PublishingDepType.EMBEDDED -> "embeds"
        }
        val method = target.javaClass.methods.firstOrNull {
            it.name == methodName && it.parameterCount == 1 &&
                (it.parameterTypes[0] == Array<String>::class.java || it.parameterTypes[0] == String::class.java)
        } ?: return
        try {
            if (method.parameterTypes[0] == Array<String>::class.java) {
                method.invoke(target, arrayOf(dep.slug))
            } else {
                method.invoke(target, dep.slug)
            }
        } catch (e: Exception) {
            (target as? Any)?.let {
                System.err.println("Prism: failed to apply publishing dep '${dep.slug}' (${dep.type}): ${e.message}")
            }
        }
    }

    fun createAggregateTask(project: Project, excludeChildren: Set<String> = emptySet()) {
        ensureAggregateTasksRegistered(project)

        project.childProjects.forEach { (name, child) ->
            if (name !in excludeChildren) {
                wirePublishTasks(project, child)
            }
        }
    }

    fun createVersionAggregate(versionProject: Project) {
        ensureAggregateTasksRegistered(versionProject)
        versionProject.childProjects.values.forEach { child ->
            wirePublishTasks(versionProject, child)
        }
    }

    fun linkAggregateToChild(parent: Project, child: Project) {
        ensureAggregateTasksRegistered(parent)
        for (taskName in LEAF_PLATFORM_TASKS) {
            val aggregateName = aggregateNameFor(taskName)
            val childAggregate = child.tasks.findByName(aggregateName) ?: continue
            parent.tasks.named(aggregateName).configure { it.dependsOn(childAggregate) }
        }
        val childAll = child.tasks.findByName(PRISM_PUBLISH_ALL) ?: return
        parent.tasks.named(PRISM_PUBLISH_ALL).configure { it.dependsOn(childAll) }
    }

    private fun ensureAggregateTasksRegistered(project: Project) {
        if (project.tasks.findByName(PRISM_PUBLISH_ALL) == null) {
            project.tasks.register(PRISM_PUBLISH_ALL) { task ->
                task.group = "publishing"
                task.description = "Publishes all mod JARs to every configured platform (Prism aggregate)"
            }
        }
        for (taskName in PRISM_AGGREGATE_TASKS) {
            if (project.tasks.findByName(taskName) == null) {
                project.tasks.register(taskName) { task ->
                    task.group = "publishing"
                    task.description = "Prism aggregate for ${taskName.removePrefix("prism").replaceFirstChar { it.lowercase() }}"
                }
                project.tasks.named(PRISM_PUBLISH_ALL).configure { it.dependsOn(project.tasks.named(taskName)) }
            }
        }
    }

    private fun wirePublishTasks(aggregateProject: Project, child: Project) {
        for (leafTaskName in LEAF_PLATFORM_TASKS) {
            val aggregateName = aggregateNameFor(leafTaskName)
            child.tasks.matching { it.name == leafTaskName }.configureEach { leafTask ->
                aggregateProject.tasks.named(aggregateName).configure { it.dependsOn(leafTask) }
            }
        }
        child.childProjects.values.forEach { grandchild ->
            wirePublishTasks(aggregateProject, grandchild)
        }
    }
}
