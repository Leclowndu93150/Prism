package dev.prism.gradle.internal.publish.gitea

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

abstract class PublishGiteaTask : DefaultTask() {
    @get:Input abstract val accessToken: Property<String>
    @get:Input abstract val apiEndpoint: Property<String>
    @get:Input abstract val repository: Property<String>
    @get:Input abstract val tagName: Property<String>
    @get:Input abstract val commitish: Property<String>
    @get:Input @get:Optional abstract val displayName: Property<String>
    @get:Input abstract val changelog: Property<String>
    @get:Input abstract val draft: Property<Boolean>
    @get:Input abstract val prerelease: Property<Boolean>
    @get:Input @get:Optional abstract val dryRun: Property<Boolean>
    @get:InputFile @get:PathSensitive(PathSensitivity.NONE) abstract val artifactFile: RegularFileProperty

    init {
        group = null
        description = "Uploads this loader's jar as an asset to a Gitea release"
    }

    @TaskAction
    fun publish() {
        val token = accessToken.get()
        val endpoint = apiEndpoint.get()
        val repo = repository.get()
        if (endpoint.isBlank() || repo.isBlank()) {
            throw IllegalStateException("Prism: Gitea apiEndpoint/repository empty for ${project.path}")
        }
        val tag = tagName.get()
        val file = artifactFile.get().asFile

        if (dryRun.getOrElse(false)) {
            logger.lifecycle("Prism [dry-run] Gitea release upload")
            logger.lifecycle("  endpoint=$endpoint repo=$repo tag=$tag file=${file.name}")
            return
        }

        val api = GiteaApi(token, endpoint, repo)
        val release = HttpUtils.retry(3, "Failed to create Gitea release") {
            api.createRelease(
                GiteaApi.CreateRelease(
                    body = changelog.orNull,
                    draft = true, // we'll flip to false after asset upload
                    name = displayName.orNull ?: tag,
                    prerelease = prerelease.get(),
                    tagName = tag,
                    targetCommitish = commitish.get(),
                ),
            )
        }

        HttpUtils.retry(3, "Failed to upload Gitea asset") {
            api.uploadAsset(release, file)
        }

        if (!draft.get()) {
            HttpUtils.retry(3, "Failed to publish Gitea release") {
                api.publishRelease(release)
            }
        }

        logger.lifecycle("Prism: Uploaded to Gitea: ${release.htmlUrl}")
        PublishResultsHolder.record(project, PublishResult("Gitea", release.htmlUrl))
    }
}
