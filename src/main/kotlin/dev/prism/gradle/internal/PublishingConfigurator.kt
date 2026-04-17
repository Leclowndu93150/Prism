package dev.prism.gradle.internal

import dev.prism.gradle.dsl.FabricConfiguration
import dev.prism.gradle.dsl.ForgeConfiguration
import dev.prism.gradle.dsl.LegacyForgeConfiguration
import dev.prism.gradle.dsl.LexForgeConfiguration
import dev.prism.gradle.dsl.LoaderConfiguration
import dev.prism.gradle.dsl.MetadataExtension
import dev.prism.gradle.dsl.NeoForgeConfiguration
import dev.prism.gradle.dsl.PublishingConfiguration
import dev.prism.gradle.dsl.PublishingDep
import dev.prism.gradle.dsl.PublishingPlatform
import dev.prism.gradle.dsl.VersionConfiguration
import dev.prism.gradle.internal.publish.curseforge.PublishCurseforgeTask
import dev.prism.gradle.internal.publish.discord.AnnounceDiscordTask
import dev.prism.gradle.internal.publish.gitea.PublishGiteaTask
import dev.prism.gradle.internal.publish.github.PublishGithubTask
import dev.prism.gradle.internal.publish.gitlab.PublishGitlabTask
import dev.prism.gradle.internal.publish.modrinth.PublishModrinthTask
import org.gradle.api.Project
import org.gradle.api.Task
import java.io.File

object PublishingConfigurator {
    private const val TASK_CURSEFORGE = "prismPublishCurseforge"
    private const val TASK_MODRINTH = "prismPublishModrinth"
    private const val TASK_GITHUB = "prismPublishGithub"
    private const val TASK_GITEA = "prismPublishGitea"
    private const val TASK_GITLAB = "prismPublishGitlab"
    private const val TASK_DISCORD = "prismAnnounceDiscord"
    private const val TASK_ALL = "prismPublishAll"

    private val PLATFORM_TASKS = listOf(TASK_CURSEFORGE, TASK_MODRINTH, TASK_GITHUB, TASK_GITEA, TASK_GITLAB)

    internal fun defaultPublishTaskName(loaderConfig: LoaderConfiguration): String = when (loaderConfig) {
        is FabricConfiguration -> "remapJar"
        is LegacyForgeConfiguration -> "reobfJar"
        is ForgeConfiguration -> "reobfJar"
        is LexForgeConfiguration -> "jar"
        is NeoForgeConfiguration -> "jar"
        else -> "jar"
    }

    internal fun selectPublishTask(
        project: Project,
        loaderConfig: LoaderConfiguration,
        publishingConfig: PublishingConfiguration? = null,
    ): Task? {
        publishingConfig?.artifactTaskName?.let { return project.tasks.findByName(it) }
        if (loaderConfig !is FabricConfiguration) {
            project.tasks.findByName("reobfShadowJar")?.let { return it }
            if (loaderConfig !is ForgeConfiguration && loaderConfig !is LegacyForgeConfiguration) {
                project.tasks.findByName("shadowJar")?.let { return it }
            }
        }
        val preferred = project.tasks.findByName(defaultPublishTaskName(loaderConfig))
        if (preferred != null) return preferred
        // Forge/LegacyForge: falling back to `jar` silently publishes the un-remapped dev jar.
        // Warn so the user knows. Fabric is exempt: on unobfuscated MC (1.21.11+) there is no
        // `remapJar` and `jar` IS the production artifact.
        val needsRemapped = loaderConfig is ForgeConfiguration || loaderConfig is LegacyForgeConfiguration
        if (needsRemapped) {
            project.logger.warn(
                "Prism: ${loaderConfig.loaderDisplayName} project '${project.path}' has no '${defaultPublishTaskName(loaderConfig)}' task. " +
                "Falling back to 'jar' will publish the un-remapped dev jar. " +
                "Declare `artifactTask(\"...\")` in publishing { } if this is intentional."
            )
        }
        return project.tasks.findByName("jar")
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

        val loaderPubDeps = when (loaderConfig) {
            is FabricConfiguration -> loaderConfig.pubDeps.deps
            is ForgeConfiguration -> loaderConfig.pubDeps.deps
            is LexForgeConfiguration -> loaderConfig.pubDeps.deps
            is NeoForgeConfiguration -> loaderConfig.pubDeps.deps
            else -> emptyList()
        }

        val allDeps = dedupeDeps(publishingConfig.pubDeps.deps + versionConfig.pubDeps.deps + loaderPubDeps)
        val dryRun = loaderProject.providers.gradleProperty("prism.publishDryRun").map { it.toBoolean() }

        val proj = loaderProject
        val changelog = publishingConfig.changelog
            ?: publishingConfig.changelogFile?.let { proj.rootProject.file(it).readText() }
            ?: ""
        val modVersion = metadata.version.ifEmpty { proj.rootProject.version.toString() }
        val mcVersions = versionConfig.minecraftVersionRange ?: listOf(versionConfig.minecraftVersion)
        val artifactFileProvider = proj.provider {
            resolvePublishFile(proj, loaderConfig, publishingConfig)
        }
        val displayNameProvider = proj.provider {
            publishingConfig.displayName ?: artifactFileProvider.orNull?.name ?: "${metadata.modId}-$modVersion"
        }

        val cfDeps = allDeps.filter { it.platform != PublishingPlatform.MODRINTH }
        val mrDeps = allDeps.filter { it.platform != PublishingPlatform.CURSEFORGE }

        fun wireArtifactDeps(t: org.gradle.api.Task) {
            proj.tasks.matching { it.name == "clean" }.configureEach { cleanTask ->
                t.dependsOn(cleanTask)
            }
            proj.afterEvaluate { p ->
                val artifactTask = selectPublishTask(p, loaderConfig, publishingConfig) ?: return@afterEvaluate
                val cleanTask = p.tasks.findByName("clean")
                if (cleanTask != null) artifactTask.mustRunAfter(cleanTask)
                t.dependsOn(artifactTask)
            }
        }

        publishingConfig.curseforgeConfig?.let { cf ->
            val cfTaskProvider = proj.tasks.register(TASK_CURSEFORGE, PublishCurseforgeTask::class.java) { t ->
                t.group = "publishing"
                cf.accessToken?.let { t.accessToken.set(it) }
                t.projectId.set(cf.projectId)
                t.minecraftVersions.set(mcVersions + cf.extraGameVersions)
                t.loaderSlug.set(loaderConfig.publishLoaderSlug)
                t.javaVersion.set(versionConfig.resolvedJavaVersion)
                t.displayName.set(displayNameProvider)
                t.modVersion.set(modVersion)
                t.changelog.set(changelog)
                t.releaseType.set(publishingConfig.type)
                t.deps.set(cfDeps)
                t.dryRun.set(dryRun)
                t.artifactFile.fileProvider(artifactFileProvider)
            }
            cfTaskProvider.configure { wireArtifactDeps(it) }
            wireIntoAll(proj, TASK_CURSEFORGE)
        }

        publishingConfig.modrinthConfig?.let { mr ->
            val mrTaskProvider = proj.tasks.register(TASK_MODRINTH, PublishModrinthTask::class.java) { t ->
                t.group = "publishing"
                mr.accessToken?.let { t.accessToken.set(it) }
                t.projectId.set(mr.projectId)
                t.minecraftVersions.set(mcVersions)
                t.loaderSlugs.set(listOf(loaderConfig.publishLoaderSlug) + mr.extraLoaders)
                t.displayName.set(displayNameProvider)
                t.modVersion.set(modVersion)
                t.changelog.set(changelog)
                t.releaseType.set(publishingConfig.type)
                t.featured.set(mr.featured)
                t.deps.set(mrDeps)
                t.dryRun.set(dryRun)
                t.artifactFile.fileProvider(artifactFileProvider)
            }
            mrTaskProvider.configure { wireArtifactDeps(it) }
            wireIntoAll(proj, TASK_MODRINTH)
        }

        publishingConfig.githubConfig?.let { gh ->
            val ghTaskProvider = proj.tasks.register(TASK_GITHUB, PublishGithubTask::class.java) { t ->
                t.group = "publishing"
                t.accessToken.set(resolveGithubToken(proj, gh.accessToken))
                t.repository.set(gh.repository)
                t.tagName.set(gh.tagName ?: modVersion)
                t.commitish.set(gh.commitish)
                t.displayName.set(displayNameProvider)
                t.changelog.set(changelog)
                t.draft.set(gh.draft)
                t.prerelease.set(gh.prerelease)
                t.reuseExistingRelease.set(gh.reuseExistingRelease)
                t.dryRun.set(dryRun)
                t.artifactFile.fileProvider(artifactFileProvider)
            }
            ghTaskProvider.configure { wireArtifactDeps(it) }
            wireIntoAll(proj, TASK_GITHUB)
        }

        publishingConfig.giteaConfig?.let { gt ->
            val gtTaskProvider = proj.tasks.register(TASK_GITEA, PublishGiteaTask::class.java) { t ->
                t.group = "publishing"
                gt.accessToken?.let { t.accessToken.set(it) }
                t.apiEndpoint.set(gt.apiEndpoint)
                t.repository.set(gt.repository)
                t.tagName.set(gt.tagName ?: modVersion)
                t.commitish.set(gt.commitish)
                t.displayName.set(displayNameProvider)
                t.changelog.set(changelog)
                t.draft.set(gt.draft)
                t.prerelease.set(gt.prerelease)
                t.dryRun.set(dryRun)
                t.artifactFile.fileProvider(artifactFileProvider)
            }
            gtTaskProvider.configure { wireArtifactDeps(it) }
            wireIntoAll(proj, TASK_GITEA)
        }

        publishingConfig.gitlabConfig?.let { gl ->
            val glTaskProvider = proj.tasks.register(TASK_GITLAB, PublishGitlabTask::class.java) { t ->
                t.group = "publishing"
                gl.accessToken?.let { t.accessToken.set(it) }
                t.apiEndpoint.set(gl.apiEndpoint)
                t.projectId.set(gl.projectId)
                t.tagName.set(gl.tagName ?: modVersion)
                t.commitish.set(gl.commitish)
                t.displayName.set(displayNameProvider)
                t.changelog.set(changelog)
                t.dryRun.set(dryRun)
                t.artifactFile.fileProvider(artifactFileProvider)
            }
            glTaskProvider.configure { wireArtifactDeps(it) }
            wireIntoAll(proj, TASK_GITLAB)
        }

        publishingConfig.discordConfig?.let { dc ->
            val discordTask = proj.tasks.register(TASK_DISCORD, AnnounceDiscordTask::class.java) { t ->
                t.group = "publishing"
                dc.webhookUrl?.let { t.webhookUrl.set(it) }
                t.username.set(dc.username)
                t.avatarUrl.set(dc.avatarUrl)
                t.content.set(dc.content)
                t.embedTitle.set(dc.embedTitle)
                t.embedDescription.set(dc.embedDescription)
                t.embedColor.set(dc.embedColor)
                t.includeProjectLinks.set(dc.includeProjectLinks)
                t.dryRun.set(dryRun)
            }
            PLATFORM_TASKS.forEach { name ->
                proj.tasks.matching { it.name == name }.configureEach { it.finalizedBy(discordTask) }
            }
        }

        ensureAggregateAllTask(proj)
    }

    private fun wireIntoAll(project: Project, taskName: String) {
        ensureAggregateAllTask(project)
        project.tasks.named(TASK_ALL).configure { it.dependsOn(project.tasks.named(taskName)) }
    }

    private fun ensureAggregateAllTask(project: Project) {
        if (project.tasks.findByName(TASK_ALL) == null) {
            project.tasks.register(TASK_ALL) { t ->
                t.group = "publishing"
                t.description = "Publishes this project to every configured platform (Prism aggregate)"
            }
        }
    }

    fun createVersionAggregate(versionProject: Project) {
        ensureAggregateTasksRegistered(versionProject)
        versionProject.childProjects.values.forEach { child -> wireChildIntoAggregates(versionProject, child) }
    }

    fun createAggregateTask(project: Project, excludeChildren: Set<String> = emptySet()) {
        ensureAggregateTasksRegistered(project)
        project.childProjects.forEach { (name, child) ->
            if (name !in excludeChildren) wireChildIntoAggregates(project, child)
        }
    }

    fun linkAggregateToChild(parent: Project, child: Project) {
        ensureAggregateTasksRegistered(parent)
        for (taskName in PLATFORM_TASKS + TASK_ALL) {
            child.tasks.matching { it.name == taskName }.configureEach { childTask ->
                parent.tasks.named(taskName).configure { it.dependsOn(childTask) }
            }
        }
    }

    private fun ensureAggregateTasksRegistered(project: Project) {
        if (project.tasks.findByName(TASK_ALL) == null) {
            project.tasks.register(TASK_ALL) { t ->
                t.group = "publishing"
                t.description = "Publishes all configured platforms (Prism aggregate)"
            }
        }
        for (taskName in PLATFORM_TASKS) {
            if (project.tasks.findByName(taskName) == null) {
                project.tasks.register(taskName) { t ->
                    t.group = "publishing"
                    t.description = "Prism aggregate for ${taskName.removePrefix("prismPublish").lowercase()}"
                }
                project.tasks.named(TASK_ALL).configure { it.dependsOn(project.tasks.named(taskName)) }
            }
        }
    }

    private fun wireChildIntoAggregates(parent: Project, child: Project) {
        for (taskName in PLATFORM_TASKS + TASK_ALL) {
            child.tasks.matching { it.name == taskName }.configureEach { childTask ->
                parent.tasks.named(taskName).configure { it.dependsOn(childTask) }
            }
        }
        child.childProjects.values.forEach { grandchild -> wireChildIntoAggregates(parent, grandchild) }
    }

    private fun dedupeDeps(deps: List<PublishingDep>): List<PublishingDep> {
        val perSlug = linkedMapOf<Pair<PublishingPlatform?, String>, PublishingDep>()
        for (dep in deps) perSlug[dep.platform to dep.slug] = dep
        return perSlug.values.toList()
    }

    private fun resolveGithubToken(project: Project, provided: org.gradle.api.provider.Provider<String>?): org.gradle.api.provider.Provider<String> {
        if (provided != null) return provided
        return project.providers.environmentVariable("GITHUB_TOKEN")
            .orElse(project.providers.environmentVariable("GH_TOKEN"))
    }
}
