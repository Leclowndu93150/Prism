package dev.prism.gradle.internal

import dev.prism.gradle.dsl.MixinOptions
import org.gradle.api.Project
import org.gradle.jvm.tasks.Jar
import org.gradle.language.jvm.tasks.ProcessResources
import java.io.File

object MixinAutoDetect {
    private val MIXIN_SOURCE_EXTENSIONS = setOf("java", "kt", "kts", "groovy")

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

    fun hasMixinSources(project: Project, commonProject: Project? = null): Boolean {
        if (hasMixinSourcesInProject(project)) return true
        return commonProject?.let(::hasMixinSourcesInProject) == true
    }

    private fun hasMixinSourcesInProject(project: Project): Boolean {
        val sourceRoots = listOf(
            project.file("src/main/java"),
            project.file("src/main/kotlin"),
            project.file("src/main/groovy"),
        )

        return sourceRoots.any { root ->
            if (!root.exists()) return@any false
            root.walkTopDown()
                .filter { it.isFile && it.extension in MIXIN_SOURCE_EXTENSIONS }
                .any { file ->
                    val text = runCatching { file.readText() }.getOrDefault("")
                    "@Mixin(" in text || "org.spongepowered.asm.mixin.Mixin" in text
                }
        }
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

    fun addMixinConfigsManifest(project: Project, mixinConfigs: List<String>) {
        if (mixinConfigs.isEmpty()) return
        val mixinConfigsStr = mixinConfigs.joinToString(",")
        project.tasks.withType(Jar::class.java).configureEach { jar ->
            jar.manifest.attributes["MixinConfigs"] = mixinConfigsStr
        }
    }

    fun findCorePluginClass(project: Project, commonProject: Project? = null): String? {
        val roots = mutableListOf<File>()
        for (lang in listOf("java", "kotlin")) {
            roots.add(project.file("src/main/$lang"))
            if (commonProject != null) roots.add(commonProject.file("src/main/$lang"))
        }
        val candidates = mutableListOf<String>()
        for (root in roots) {
            if (!root.exists()) continue
            root.walkTopDown()
                .filter { it.isFile && it.extension in setOf("java", "kt") }
                .forEach { file ->
                    val text = runCatching { file.readText() }.getOrDefault("")
                    if (!hasLoadingPluginMarker(text)) return@forEach
                    val fqn = extractFqn(file, text) ?: return@forEach
                    candidates.add(fqn)
                }
        }
        val first = candidates.firstOrNull()
        if (first != null && candidates.size > 1) {
            project.logger.warn(
                "Prism: Found multiple IFMLLoadingPlugin classes ($candidates); picking '$first'. " +
                "Override with legacyForge { coreMod(\"...\") }."
            )
        }
        return first
    }

    private fun hasLoadingPluginMarker(text: String): Boolean {
        return "@IFMLLoadingPlugin.Name(" in text ||
            "IFMLLoadingPlugin.Name(" in text ||
            "implements IFMLLoadingPlugin" in text ||
            ": IFMLLoadingPlugin" in text ||
            "net.minecraftforge.fml.relauncher.IFMLLoadingPlugin" in text
    }

    private fun extractFqn(file: File, text: String): String? {
        val pkg = Regex("""package\s+([\w.]+)""").find(text)?.groupValues?.get(1) ?: return null
        val simpleName = file.nameWithoutExtension
        return "$pkg.$simpleName"
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
