package dev.prism.gradle.internal

import dev.prism.gradle.dsl.MixinOptions
import org.gradle.api.Project
import org.gradle.language.jvm.tasks.ProcessResources
import java.io.File

object MixinAutoDetect {
    fun findMixinConfigs(project: Project): List<String> {
        val resourcesDir = project.file("src/main/resources")
        return findMixinConfigsRecursive(resourcesDir)
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

    fun resolveMixinConfigs(project: Project, commonProject: Project? = null, mixinOptions: MixinOptions? = null): List<String> {
        val mixinConfigs = linkedSetOf<String>()
        if (mixinOptions?.autoDetect != false) {
            mixinConfigs.addAll(findMixinConfigs(project))
            if (commonProject != null) {
                mixinConfigs.addAll(findMixinConfigs(commonProject))
            }
        }
        mixinOptions?.explicitConfigs?.forEach(mixinConfigs::add)
        return mixinConfigs.toList()
    }

    fun injectNeoForgeMixins(project: Project, mixinConfigs: List<String>) {
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

    fun injectFabricMixins(project: Project, mixinConfigs: List<String>) {
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
