package dev.prism.gradle.internal.publish.gitlab

import dev.prism.gradle.internal.publish.HttpUtils
import dev.prism.gradle.internal.publish.PublishResult
import dev.prism.gradle.internal.publish.PublishResultsHolder
import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction

abstract class PublishGitlabTask : DefaultTask() {
    @get:Input abstract val accessToken: Property<String>
    @get:Input abstract val apiEndpoint: Property<String>
    @get:Input abstract val projectId: Property<Long>
    @get:Input abstract val tagName: Property<String>
    @get:Input abstract val commitish: Property<String>
    @get:Input @get:Optional abstract val displayName: Property<String>
    @get:Input abstract val changelog: Property<String>
    @get:Input @get:Optional abstract val dryRun: Property<Boolean>
    @get:InputFile @get:PathSensitive(PathSensitivity.NONE) abstract val artifactFile: RegularFileProperty

    init {
        group = null
        description = "Uploads this loader's jar as an asset to a GitLab release"
    }

    @TaskAction
    fun publish() {
        val token = accessToken.get()
        val endpoint = apiEndpoint.get()
        val pid = projectId.get()
        if (pid <= 0L) throw IllegalStateException("Prism: GitLab projectId is empty for ${project.path}")
        val tag = tagName.get()
        val file = artifactFile.get().asFile

        if (dryRun.getOrElse(false)) {
            logger.lifecycle("Prism [dry-run] GitLab release upload")
            logger.lifecycle("  endpoint=$endpoint projectId=$pid tag=$tag file=${file.name}")
            return
        }

        val api = GitlabApi(token, endpoint)

        val asset = HttpUtils.retry(3, "Failed to upload GitLab asset") {
            api.uploadAsset(pid, file)
        }

        val existing = runCatching { api.getRelease(pid, tag) }.getOrNull()
        val release = if (existing == null) {
            HttpUtils.retry(3, "Failed to create GitLab release") {
                api.createRelease(
                    pid,
                    GitlabApi.CreateReleaseRequest(
                        name = displayName.orNull ?: tag,
                        tagName = tag,
                        description = (changelog.orNull ?: "") + "\n\n[${asset.name}](${asset.url})",
                        ref = commitish.get(),
                    ),
                )
            }
        } else {
            existing
        }

        logger.lifecycle("Prism: Uploaded to GitLab: ${asset.url}")
        PublishResultsHolder.record(project, PublishResult("GitLab", asset.url))
    }
}
