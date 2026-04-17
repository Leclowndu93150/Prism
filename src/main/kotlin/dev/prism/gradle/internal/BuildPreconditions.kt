package dev.prism.gradle.internal

import dev.prism.gradle.dsl.DependencyBlock
import dev.prism.gradle.dsl.FabricConfiguration
import dev.prism.gradle.dsl.ForgeConfiguration
import dev.prism.gradle.dsl.LegacyForgeConfiguration
import dev.prism.gradle.dsl.LexForgeConfiguration
import dev.prism.gradle.dsl.LoaderConfiguration
import dev.prism.gradle.dsl.NeoForgeConfiguration
import dev.prism.gradle.dsl.VersionConfiguration
import groovy.json.JsonSlurper
import org.gradle.api.Project
import java.io.File
import java.util.jar.JarFile

object BuildPreconditions {

    fun check(
        rootProject: Project,
        mcVersion: String,
        versionConfig: VersionConfiguration,
        projectPathPrefix: String,
    ) {
        val isSingleLoader = versionConfig.loaders.size == 1 &&
            rootProject.findProject("$projectPathPrefix:$mcVersion:common") == null

        val commonProject = if (isSingleLoader) null
        else rootProject.findProject("$projectPathPrefix:$mcVersion:common")

        val loaderProjects = mutableMapOf<LoaderConfiguration, Project>()
        if (isSingleLoader) {
            val p = rootProject.findProject("$projectPathPrefix:$mcVersion")
            if (p != null) loaderProjects[versionConfig.loaders.first()] = p
        } else {
            for (loader in versionConfig.loaders) {
                val p = rootProject.findProject("$projectPathPrefix:$mcVersion:${loader.loaderName}")
                if (p != null) loaderProjects[loader] = p
            }
        }

        checkForgeMixinRefmaps(mcVersion, versionConfig, commonProject, loaderProjects)
        checkPackMcmeta(mcVersion, commonProject, loaderProjects)
        checkForgeModDependencies(mcVersion, versionConfig, commonProject, loaderProjects)
    }

    private fun checkForgeMixinRefmaps(
        mcVersion: String,
        versionConfig: VersionConfiguration,
        commonProject: Project?,
        loaderProjects: Map<LoaderConfiguration, Project>,
    ) {
        if (versionConfig.forgeConfig == null) return

        val targets = mutableListOf<Project>()
        if (commonProject != null) targets.add(commonProject)
        val forgeProject = loaderProjects.entries.firstOrNull { it.key is ForgeConfiguration }?.value
        if (forgeProject != null) targets.add(forgeProject)

        for (project in targets) {
            val resourcesDir = project.file("src/main/resources")
            if (!resourcesDir.exists()) continue
            val mixinFiles = resourcesDir.walkTopDown()
                .filter { it.isFile && it.name.endsWith(".mixins.json") }
                .toList()
            for (file in mixinFiles) {
                verifyMixinRefmap(mcVersion, project, resourcesDir, file)
            }
        }
    }

    private fun verifyMixinRefmap(mcVersion: String, project: Project, resourcesDir: File, file: File) {
        val relPath = file.relativeTo(resourcesDir).path.replace("\\", "/")
        val parsed = try {
            JsonSlurper().parse(file)
        } catch (e: Exception) {
            throw IllegalStateException(
                "Prism: mixin config '$relPath' in project '${project.path}' (version '$mcVersion') is not valid JSON: ${e.message}"
            )
        }

        if (parsed !is Map<*, *>) {
            throw IllegalStateException(
                "Prism: mixin config '$relPath' in project '${project.path}' (version '$mcVersion') must be a JSON object."
            )
        }

        val hasMixins = listOf("mixins", "client", "server").any { key ->
            val value = parsed[key]
            value is List<*> && value.isNotEmpty()
        }
        if (!hasMixins) return

        val refmap = parsed["refmap"] as? String
        if (refmap.isNullOrBlank()) {
            throw IllegalStateException(
                "Prism: mixin config '$relPath' in project '${project.path}' (version '$mcVersion') is missing a 'refmap' entry.\n" +
                "  MDG Legacy (Forge <1.21.1) requires the refmap to be declared in the JSON:\n" +
                "    { \"refmap\": \"<modid>.refmap.json\", \"mixins\": [...] }\n" +
                "  Fabric does not need this; this check applies to common + forge only."
            )
        }
    }

    private fun checkPackMcmeta(
        mcVersion: String,
        commonProject: Project?,
        loaderProjects: Map<LoaderConfiguration, Project>,
    ) {
        val targets = mutableListOf<Project>()
        if (commonProject != null) targets.add(commonProject)
        targets.addAll(loaderProjects.values)

        for (project in targets) {
            val resourcesDir = project.file("src/main/resources")
            if (!resourcesDir.exists()) continue
            if (!hasAssetsOrDataContent(resourcesDir)) continue
            val packMcmeta = File(resourcesDir, "pack.mcmeta")
            if (!packMcmeta.exists()) {
                throw IllegalStateException(
                    "Prism: version '$mcVersion' project '${project.path}' has assets/ or data/ resources but is missing 'src/main/resources/pack.mcmeta'.\n" +
                    "  Minecraft requires pack.mcmeta when resource packs or data packs are present.\n" +
                    "  Create src/main/resources/pack.mcmeta with:\n" +
                    "    { \"pack\": { \"pack_format\": <N>, \"description\": \"<modid> resources\" } }"
                )
            }
        }
    }

    private fun hasAssetsOrDataContent(resourcesDir: File): Boolean {
        for (name in listOf("assets", "data")) {
            val dir = File(resourcesDir, name)
            if (!dir.exists() || !dir.isDirectory) continue
            val anyFile = dir.walkTopDown().any { it.isFile }
            if (anyFile) return true
        }
        return false
    }

    private fun checkForgeModDependencies(
        mcVersion: String,
        versionConfig: VersionConfiguration,
        commonProject: Project?,
        loaderProjects: Map<LoaderConfiguration, Project>,
    ) {
        if (versionConfig.forgeConfig == null) return

        val forgeEntry = loaderProjects.entries.firstOrNull { it.key is ForgeConfiguration }
        if (forgeEntry != null) {
            scanDependencyBlock(mcVersion, forgeEntry.value, forgeEntry.key.deps())
        }

        if (commonProject != null) {
            scanDependencyBlock(mcVersion, commonProject, versionConfig.commonDeps)
        } else if (forgeEntry != null) {
            scanDependencyBlock(mcVersion, forgeEntry.value, versionConfig.commonDeps)
        }
    }

    private fun LoaderConfiguration.deps(): DependencyBlock = when (this) {
        is FabricConfiguration -> deps
        is ForgeConfiguration -> deps
        is LexForgeConfiguration -> deps
        is NeoForgeConfiguration -> deps
        is LegacyForgeConfiguration -> deps
        else -> DependencyBlock()
    }

    private data class DepEntry(val configName: String, val notation: String)

    private fun scanDependencyBlock(mcVersion: String, project: Project, block: DependencyBlock) {
        val nonMod = mutableListOf<DepEntry>()
        block.apis.forEach { nonMod.add(DepEntry("api", it)) }
        block.implementations.forEach { nonMod.add(DepEntry("implementation", it)) }
        block.compileOnlyApis.forEach { nonMod.add(DepEntry("compileOnlyApi", it)) }
        block.compileOnlys.forEach { nonMod.add(DepEntry("compileOnly", it)) }
        block.runtimeOnlys.forEach { nonMod.add(DepEntry("runtimeOnly", it)) }
        block.customDeps.forEach { nonMod.add(DepEntry(it.configuration, it.dependency)) }

        for (entry in nonMod) {
            if (shouldSkipNotation(entry.notation)) continue
            val jars = resolveJars(project, entry.notation)
            for (jar in jars) {
                if (isForgeModJar(jar)) {
                    throw IllegalStateException(
                        "Prism: version '$mcVersion' project '${project.path}' declares Forge mod '${entry.notation}' via non-'mod' configuration '${entry.configName}'.\n" +
                        "  On MDG Legacy (Forge <1.21.1), Forge mod dependencies MUST use the remapping variant (mod*), otherwise the mod will not be remapped and will crash in production.\n" +
                        "  Change it to:\n" +
                        "    dependencies {\n" +
                        "        mod<Impl|Api|CompileOnly|RuntimeOnly>(\"${entry.notation}\")\n" +
                        "    }"
                    )
                }
            }
        }
    }

    private fun shouldSkipNotation(notation: String): Boolean {
        val parts = notation.split(":")
        if (parts.size < 3) return true
        val classifier = parts.getOrNull(3)
        return classifier == "sources" || classifier == "javadoc"
    }

    private fun resolveJars(project: Project, notation: String): List<File> {
        return try {
            val dep = project.dependencies.create(notation)
            val config = project.configurations.detachedConfiguration(dep)
            config.isTransitive = false
            config.resolvedConfiguration.lenientConfiguration.artifacts
                .mapNotNull { it.file.takeIf { f -> f.exists() && f.name.endsWith(".jar") } }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun isForgeModJar(jar: File): Boolean {
        return try {
            JarFile(jar).use { jf ->
                jf.getEntry("META-INF/mods.toml") != null
            }
        } catch (_: Exception) {
            false
        }
    }
}
