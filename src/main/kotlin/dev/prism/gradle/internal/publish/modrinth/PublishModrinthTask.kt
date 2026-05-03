package dev.prism.gradle.internal.publish.modrinth

import dev.prism.gradle.dsl.PublishingDep
import dev.prism.gradle.dsl.PublishingPlatform
import dev.prism.gradle.dsl.ReleaseType
import dev.prism.gradle.internal.publish.HttpUtils
import dev.prism.gradle.internal.publish.PublishResult
import dev.prism.gradle.internal.publish.PublishResultsHolder
import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault

@DisableCachingByDefault(because = "Network upload task")
abstract class PublishModrinthTask : DefaultTask() {
    @get:Input abstract val accessToken: Property<String>
    @get:Input abstract val projectId: Property<String>
    @get:Input abstract val minecraftVersions: ListProperty<String>
    @get:Input abstract val loaderSlugs: ListProperty<String>
    @get:Input @get:Optional abstract val displayName: Property<String>
    @get:Input abstract val modVersion: Property<String>
    @get:Input abstract val changelog: Property<String>
    @get:Input abstract val releaseType: Property<ReleaseType>
    @get:Input abstract val featured: Property<Boolean>
    @get:Input abstract val deps: ListProperty<PublishingDep>
    @get:Input @get:Optional abstract val dryRun: Property<Boolean>
    @get:InputFile @get:PathSensitive(PathSensitivity.NONE) abstract val artifactFile: RegularFileProperty

    init {
        group = null
        description = "Uploads this loader's jar to Modrinth"
    }

    @TaskAction
    fun publish() {
        val token = accessToken.get()
        val pid = projectId.get()
        if (pid.isBlank()) throw IllegalStateException("Prism: Modrinth projectId is empty for ${project.path}")
        val file = artifactFile.get().asFile
        if (!file.exists()) throw IllegalStateException("Prism: artifact file ${file.absolutePath} does not exist")

        val dryRunEnabled = dryRun.getOrElse(false)
        val displayNameVal = displayName.orNull ?: file.nameWithoutExtension

        val api = ModrinthApi(token, "https://api.modrinth.com/v2")
        val resolvedDeps = buildDeps(deps.getOrElse(emptyList()), api)

        if (dryRunEnabled) {
            logger.lifecycle("Prism [dry-run] Modrinth upload")
            logger.lifecycle("  projectId=$pid file=${file.name}")
            logger.lifecycle("  minecraftVersions=${minecraftVersions.get()}")
            logger.lifecycle("  loaders=${loaderSlugs.get()}")
            logger.lifecycle("  deps=${resolvedDeps.map { "${it.projectId}:${it.dependencyType}" }}")
            return
        }
        val meta = ModrinthApi.CreateVersion(
            name = displayNameVal,
            versionNumber = modVersion.get(),
            changelog = changelog.orNull,
            dependencies = resolvedDeps,
            gameVersions = minecraftVersions.get(),
            versionType = ModrinthApi.VersionType.valueOf(releaseType.get()),
            loaders = loaderSlugs.get(),
            featured = featured.getOrElse(true),
            projectId = pid,
            fileParts = listOf("primary"),
            primaryFile = "primary",
        )

        val response = HttpUtils.retry(3, "Failed to upload to Modrinth") {
            api.createVersion(meta, mapOf("primary" to file.toPath()))
        }
        val url = "https://modrinth.com/mod/${response.projectId}/version/${response.id}"
        logger.lifecycle("Prism: Uploaded to Modrinth: $url")
        PublishResultsHolder.record(project, PublishResult("Modrinth", url))
    }

    private fun buildDeps(deps: List<PublishingDep>, api: ModrinthApi): List<ModrinthApi.Dependency> {
        return deps.filter { it.platform != PublishingPlatform.CURSEFORGE }.map { d ->
            val resolvedId = try {
                api.checkProject(d.slug).id
            } catch (e: Exception) {
                logger.warn("Prism: could not resolve Modrinth project ID for slug '${d.slug}', using slug as-is: ${e.message}")
                d.slug
            }
            ModrinthApi.Dependency(
                projectId = resolvedId,
                dependencyType = ModrinthApi.DependencyType.valueOf(d.type),
            )
        }
    }
}
