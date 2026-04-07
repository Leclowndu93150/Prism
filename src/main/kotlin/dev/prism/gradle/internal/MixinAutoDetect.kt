package dev.prism.gradle.internal

import org.gradle.api.Project
import org.gradle.language.jvm.tasks.ProcessResources
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

    fun injectNeoForgeMixins(project: Project, commonProject: Project? = null) {
        val mixinConfigs = findMixinConfigs(project).toMutableList()
        if (commonProject != null) {
            mixinConfigs.addAll(findMixinConfigs(commonProject))
        }
        if (mixinConfigs.isEmpty()) return

        project.tasks.named("processResources", ProcessResources::class.java) { task ->
            task.doLast {
                val tomlFile = File(task.destinationDir, "META-INF/neoforge.mods.toml")
                if (!tomlFile.exists()) return@doLast

                val content = tomlFile.readText()
                val missingConfigs = mixinConfigs.filter { config ->
                    !content.contains("config=\"$config\"") && !content.contains("config = \"$config\"")
                }

                if (missingConfigs.isNotEmpty()) {
                    val mixinEntries = missingConfigs.joinToString("\n") { config ->
                        "\n[[mixins]]\nconfig = \"$config\""
                    }
                    tomlFile.appendText(mixinEntries)
                    project.logger.lifecycle("Prism: Auto-registered ${missingConfigs.size} mixin config(s) in neoforge.mods.toml: $missingConfigs")
                }
            }
        }
    }

    fun injectFabricMixins(project: Project, commonProject: Project? = null) {
        val mixinConfigs = findMixinConfigs(project).toMutableList()
        if (commonProject != null) {
            mixinConfigs.addAll(findMixinConfigs(commonProject))
        }
        if (mixinConfigs.isEmpty()) return

        project.tasks.named("processResources", ProcessResources::class.java) { task ->
            task.doLast {
                val jsonFile = File(task.destinationDir, "fabric.mod.json")
                if (!jsonFile.exists()) return@doLast

                val content = jsonFile.readText()
                val missingConfigs = mixinConfigs.filter { config ->
                    !content.contains("\"$config\"")
                }

                if (missingConfigs.isNotEmpty() && !content.contains("\"mixins\"")) {
                    val mixinsArray = missingConfigs.joinToString(", ") { "\"$it\"" }
                    val injected = content.trimEnd().removeSuffix("}") + ",\n  \"mixins\": [$mixinsArray]\n}"
                    jsonFile.writeText(injected)
                    project.logger.lifecycle("Prism: Auto-registered ${missingConfigs.size} mixin config(s) in fabric.mod.json: $missingConfigs")
                }
            }
        }
    }
}
