package dev.prism.gradle.internal

import org.gradle.api.Project
import org.gradle.api.invocation.Gradle

object PrismLayout {
    private const val ROOT_VERSION_PROPERTY = "dev.prism.rootVersion"

    fun markRootVersion(gradle: Gradle, mcVersion: String) {
        gradle.extensions.extraProperties.set(ROOT_VERSION_PROPERTY, mcVersion)
    }

    fun rootVersion(project: Project): String? {
        val properties = project.gradle.extensions.extraProperties
        return if (properties.has(ROOT_VERSION_PROPERTY)) properties.get(ROOT_VERSION_PROPERTY) as String else null
    }

    fun isRootVersion(rootProject: Project, projectPathPrefix: String, mcVersion: String): Boolean =
        projectPathPrefix.isEmpty() && rootVersion(rootProject) == mcVersion

    fun singleProject(rootProject: Project, projectPathPrefix: String, mcVersion: String): Project? =
        if (isRootVersion(rootProject, projectPathPrefix, mcVersion)) rootProject
        else rootProject.findProject("$projectPathPrefix:$mcVersion")
}
