package dev.prism.gradle.internal.publish

import org.gradle.api.Project

data class PublishResult(
    val platform: String,
    val url: String,
)

object PublishResultsHolder {
    private val byProject = mutableMapOf<String, MutableList<PublishResult>>()

    fun record(project: Project, result: PublishResult) {
        byProject.getOrPut(project.path) { mutableListOf() }.add(result)
    }

    fun collect(project: Project): List<PublishResult> {
        val out = mutableListOf<PublishResult>()
        out.addAll(byProject[project.path].orEmpty())
        project.childProjects.values.forEach { out.addAll(collect(it)) }
        return out
    }
}
