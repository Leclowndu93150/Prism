package dev.prism.gradle.internal.publish.curseforge

import dev.prism.gradle.dsl.PublishingDep
import dev.prism.gradle.dsl.PublishingPlatform
import dev.prism.gradle.dsl.ReleaseType
import dev.prism.gradle.internal.publish.HttpUtils
import dev.prism.gradle.internal.publish.PublishResult
import dev.prism.gradle.internal.publish.PublishResultsHolder
import org.gradle.api.DefaultTask
import org.gradle.api.JavaVersion
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import java.io.File

abstract class PublishCurseforgeTask : DefaultTask() {
    @get:Input abstract val accessToken: Property<String>
    @get:Input abstract val projectId: Property<String>
    @get:Input abstract val minecraftVersions: ListProperty<String>
    @get:Input abstract val loaderSlug: Property<String>
    @get:Input @get:Optional abstract val javaVersion: Property<Int>
    @get:Input @get:Optional abstract val displayName: Property<String>
    @get:Input abstract val modVersion: Property<String>
    @get:Input abstract val changelog: Property<String>
    @get:Input abstract val releaseType: Property<ReleaseType>
    @get:Input abstract val deps: ListProperty<PublishingDep>
    @get:Input @get:Optional abstract val dryRun: Property<Boolean>
    @get:InputFile @get:PathSensitive(PathSensitivity.NONE) abstract val artifactFile: RegularFileProperty

    init {
        group = null
        description = "Uploads this loader's jar to CurseForge"
    }

    @TaskAction
    fun publish() {
        val token = accessToken.get()
        val pid = projectId.get()
        if (pid.isBlank()) throw IllegalStateException("Prism: CurseForge projectId is empty for ${project.path}")
        val file = artifactFile.get().asFile
        if (!file.exists()) throw IllegalStateException("Prism: artifact file ${file.absolutePath} does not exist")

        val relations = buildRelations(deps.getOrElse(emptyList()))
        val dryRunEnabled = dryRun.getOrElse(false)

        if (dryRunEnabled) {
            logger.lifecycle("Prism [dry-run] CurseForge upload")
            logger.lifecycle("  projectId=$pid file=${file.name}")
            logger.lifecycle("  minecraftVersions=${minecraftVersions.get()}")
            logger.lifecycle("  loader=${loaderSlug.get()}  java=${javaVersion.orNull}")
            logger.lifecycle("  relations=${relations?.projects?.map { "${it.slug}:${it.type}" }}")
            return
        }

        val api = CurseforgeApi(token, "https://minecraft.curseforge.com")
        val versions = HttpUtils.retry(3, "Failed to load CurseForge versions") {
            CurseforgeVersions(api.getVersionTypes(), api.getGameVersions())
        }

        val gameVersionIds = mutableListOf<Int>()
        for (v in minecraftVersions.get()) gameVersionIds.add(versions.getMinecraftVersion(v))
        runCatching { gameVersionIds.add(versions.getModLoaderVersion(loaderSlug.get())) }
        runCatching { gameVersionIds.add(versions.getClientVersion()) }
        runCatching { gameVersionIds.add(versions.getServerVersion()) }
        javaVersion.orNull?.let { j ->
            val jv = JavaVersion.toVersion(j)
            runCatching { gameVersionIds.add(versions.getJavaVersion(jv)) }
        }

        val metadata = CurseforgeApi.UploadFileMetadata(
            changelog = changelog.getOrElse(""),
            changelogType = CurseforgeApi.ChangelogType.MARKDOWN,
            displayName = displayName.orNull ?: "${file.nameWithoutExtension}",
            gameVersions = gameVersionIds,
            releaseType = CurseforgeApi.ReleaseType.valueOf(releaseType.get()),
            relations = relations,
        )

        val response = HttpUtils.retry(3, "Failed to upload to CurseForge") {
            api.uploadFile(pid, file.toPath(), metadata)
        }
        val url = "https://www.curseforge.com/minecraft/mc-mods/$pid/files/${response.id}"
        logger.lifecycle("Prism: Uploaded to CurseForge: $url")
        PublishResultsHolder.record(project, PublishResult("CurseForge", url))
    }

    private fun buildRelations(deps: List<PublishingDep>): CurseforgeApi.UploadFileRelations? {
        val relevant = deps.filter { it.platform != PublishingPlatform.MODRINTH }
        if (relevant.isEmpty()) return null
        return CurseforgeApi.UploadFileRelations(
            relevant.map { CurseforgeApi.ProjectFileRelation(it.slug, CurseforgeApi.RelationType.valueOf(it.type)) }
        )
    }
}
