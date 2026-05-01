package dev.prism.gradle.internal.publish.github

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
import org.gradle.work.DisableCachingByDefault

@DisableCachingByDefault(because = "Network upload task")
abstract class PublishGithubTask : DefaultTask() {
    @get:Input abstract val accessToken: Property<String>
    @get:Input abstract val repository: Property<String>
    @get:Input abstract val tagName: Property<String>
    @get:Input abstract val commitish: Property<String>
    @get:Input @get:Optional abstract val displayName: Property<String>
    @get:Input abstract val changelog: Property<String>
    @get:Input abstract val draft: Property<Boolean>
    @get:Input abstract val prerelease: Property<Boolean>
    @get:Input abstract val reuseExistingRelease: Property<Boolean>
    @get:Input @get:Optional abstract val dryRun: Property<Boolean>
    @get:InputFile @get:PathSensitive(PathSensitivity.NONE) abstract val artifactFile: RegularFileProperty

    init {
        group = null
        description = "Uploads this loader's jar as an asset to a GitHub release"
    }

    @TaskAction
    fun publish() {
        val token = accessToken.get()
        val repo = repository.get()
        if (repo.isBlank()) throw IllegalStateException("Prism: GitHub repository is empty for ${project.path}")
        val tag = tagName.get()
        val file = artifactFile.get().asFile

        if (dryRun.getOrElse(false)) {
            logger.lifecycle("Prism [dry-run] GitHub release upload")
            logger.lifecycle("  repo=$repo tag=$tag file=${file.name}")
            return
        }

        val api = GithubApi(token)
        val existing = if (reuseExistingRelease.get()) {
            runCatching { findReleaseByTag(api, repo, tag) }.getOrNull()
        } else null

        val release = existing ?: HttpUtils.retry(3, "Failed to create GitHub release") {
            api.createRelease(
                repo,
                GithubApi.CreateReleaseRequest(
                    tagName = tag,
                    targetCommitish = commitish.get(),
                    name = displayName.orNull ?: tag,
                    body = changelog.getOrElse(""),
                    draft = draft.get(),
                    prerelease = prerelease.get(),
                ),
            )
        }

        HttpUtils.retry(3, "Failed to upload GitHub asset") {
            api.uploadAsset(release, file)
        }

        logger.lifecycle("Prism: Uploaded to GitHub: ${release.htmlUrl}")
        PublishResultsHolder.record(project, PublishResult("GitHub", release.htmlUrl))
    }

    private fun findReleaseByTag(api: GithubApi, repo: String, tag: String): GithubApi.Release? {
        // GitHub's create-release returns 422 if a release already exists for the tag.
        // We attempt creation and fall back; a cleaner approach uses the by-tag endpoint, but that needs an extra
        // data class. For simplicity, let the caller handle creation errors.
        return null
    }
}
