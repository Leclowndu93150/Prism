package dev.prism.gradle.internal.publish

data class PublishResult(
    val platform: String,
    val url: String,
)

object PublishResultsHolder {
    private val byProject = mutableMapOf<String, MutableList<PublishResult>>()

    @Synchronized
    fun record(taskPath: String, result: PublishResult) {
        byProject.getOrPut(projectPathOf(taskPath)) { mutableListOf() }.add(result)
    }

    @Synchronized
    fun collect(taskPath: String): List<PublishResult> {
        val projectPath = projectPathOf(taskPath)
        val childPrefix = if (projectPath == ":") ":" else "$projectPath:"
        return byProject.filterKeys { it == projectPath || it.startsWith(childPrefix) }.values.flatten()
    }

    private fun projectPathOf(taskPath: String): String = taskPath.substringBeforeLast(':').ifEmpty { ":" }
}
