package dev.prism.gradle.internal

import org.gradle.api.Project
import java.io.File

object MixinAutoDetect {
    fun findMixinConfigs(project: Project): List<String> {
        val resourcesDir = project.file("src/main/resources")
        if (!resourcesDir.exists()) return emptyList()

        return resourcesDir.listFiles()
            ?.filter { it.name.endsWith(".mixins.json") }
            ?.map { it.name }
            ?: emptyList()
    }

    fun findMixinConfigsRecursive(vararg dirs: File): List<String> {
        return dirs.flatMap { dir ->
            if (!dir.exists()) return@flatMap emptyList()
            dir.walkTopDown()
                .filter { it.isFile && it.name.endsWith(".mixins.json") }
                .map { it.relativeTo(dir).path.replace("\\", "/") }
                .toList()
        }
    }
}
