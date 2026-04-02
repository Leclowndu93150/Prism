package dev.prism.gradle.dsl

import org.gradle.api.Action
import org.gradle.api.initialization.Settings
import java.io.File

class PrismSettingsExtension(private val settings: Settings) {
    internal val versions = mutableMapOf<String, SettingsVersionConfig>()
    internal var hasSharedCommon = false

    fun sharedCommon() {
        if (hasSharedCommon) return
        hasSharedCommon = true
        settings.include(":common")
        settings.project(":common").projectDir = File(settings.settingsDir, "common")
    }

    fun version(mcVersion: String, action: Action<SettingsVersionConfig>) {
        val config = versions.getOrPut(mcVersion) { SettingsVersionConfig(mcVersion) }
        action.execute(config)

        if (config.isSingleLoader) {
            registerSingleProject(mcVersion, config.singleLoaderName!!)
        } else {
            registerSubproject(mcVersion, "common")
            if (config.hasFabric) registerSubproject(mcVersion, "fabric")
            if (config.hasForge) registerSubproject(mcVersion, "forge")
            if (config.hasNeoForge) registerSubproject(mcVersion, "neoforge")
        }
    }

    private fun registerSingleProject(mcVersion: String, loader: String) {
        val path = ":$mcVersion"
        if (settings.findProject(path) != null) return

        settings.include(path)
        settings.project(path).projectDir = File(settings.settingsDir, "versions/$mcVersion")
    }

    private fun registerSubproject(mcVersion: String, loader: String) {
        val path = ":$mcVersion:$loader"
        if (settings.findProject(path) != null) return

        settings.include(path)
        settings.project(path).projectDir = File(settings.settingsDir, "versions/$mcVersion/$loader")

        val parentPath = ":$mcVersion"
        val parentProject = settings.findProject(parentPath)
        if (parentProject != null) {
            parentProject.projectDir = File(settings.settingsDir, "versions/$mcVersion")
        }
    }
}

class SettingsVersionConfig(val minecraftVersion: String) {
    var hasFabric = false; private set
    var hasForge = false; private set
    var hasNeoForge = false; private set
    var hasCommon = false; private set

    fun common() { hasCommon = true }
    fun fabric() { hasFabric = true }
    fun forge() { hasForge = true }
    fun neoforge() { hasNeoForge = true }

    val loaderCount: Int
        get() = listOf(hasFabric, hasForge, hasNeoForge).count { it }

    val isSingleLoader: Boolean
        get() = loaderCount == 1 && !hasCommon

    val singleLoaderName: String?
        get() = when {
            !isSingleLoader -> null
            hasFabric -> "fabric"
            hasForge -> "forge"
            hasNeoForge -> "neoforge"
            else -> null
        }
}
