package dev.prism.gradle.dsl

import org.gradle.api.Action
import org.gradle.api.initialization.Settings
import java.io.File

class PrismSettingsExtension(private val settings: Settings) {
    internal val versions = mutableMapOf<String, SettingsVersionConfig>()
    internal val modules = mutableMapOf<String, SettingsModuleConfig>()
    internal var hasSharedCommon = false
    internal var sharedCommonPath: String = "common"

    fun sharedCommon() {
        sharedCommon("common")
    }

    fun sharedCommon(path: String) {
        if (hasSharedCommon) return
        hasSharedCommon = true
        sharedCommonPath = path
        settings.include(":common")
        settings.project(":common").projectDir = File(settings.settingsDir, path)
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
            if (config.hasLegacyForge) registerSubproject(mcVersion, "legacyforge")
        }
    }

    fun mod(moduleName: String, action: Action<SettingsModuleConfig>) {
        val config = modules.getOrPut(moduleName) { SettingsModuleConfig(moduleName) }
        action.execute(config)

        for ((mcVersion, versionConfig) in config.versions) {
            if (versionConfig.isSingleLoader) {
                registerModuleSingleProject(moduleName, mcVersion)
            } else {
                registerModuleSubproject(moduleName, mcVersion, "common")
                if (versionConfig.hasFabric) registerModuleSubproject(moduleName, mcVersion, "fabric")
                if (versionConfig.hasForge) registerModuleSubproject(moduleName, mcVersion, "forge")
                if (versionConfig.hasNeoForge) registerModuleSubproject(moduleName, mcVersion, "neoforge")
                if (versionConfig.hasLegacyForge) registerModuleSubproject(moduleName, mcVersion, "legacyforge")
            }
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

    private fun ensureModuleParent(moduleName: String) {
        val modulePath = ":$moduleName"
        settings.include(modulePath)
        settings.project(modulePath).projectDir = File(settings.settingsDir, "modules/$moduleName")
    }

    private fun registerModuleSingleProject(moduleName: String, mcVersion: String) {
        val path = ":$moduleName:$mcVersion"
        if (settings.findProject(path) != null) return

        ensureModuleParent(moduleName)
        settings.include(path)
        settings.project(path).projectDir = File(settings.settingsDir, "modules/$moduleName/versions/$mcVersion")
    }

    private fun registerModuleSubproject(moduleName: String, mcVersion: String, loader: String) {
        val path = ":$moduleName:$mcVersion:$loader"
        if (settings.findProject(path) != null) return

        ensureModuleParent(moduleName)
        settings.include(path)
        settings.project(path).projectDir = File(settings.settingsDir, "modules/$moduleName/versions/$mcVersion/$loader")

        val versionPath = ":$moduleName:$mcVersion"
        settings.include(versionPath)
        settings.project(versionPath).projectDir = File(settings.settingsDir, "modules/$moduleName/versions/$mcVersion")
    }
}

class SettingsVersionConfig(val minecraftVersion: String) {
    var hasFabric = false; private set
    var hasForge = false; private set
    var hasNeoForge = false; private set
    var hasLegacyForge = false; private set
    var hasCommon = false; private set

    fun common() { hasCommon = true }
    fun fabric() { hasFabric = true }
    fun forge() { hasForge = true }
    fun neoforge() { hasNeoForge = true }
    fun legacyForge() { hasLegacyForge = true }

    val loaderCount: Int
        get() = listOf(hasFabric, hasForge, hasNeoForge, hasLegacyForge).count { it }

    val isSingleLoader: Boolean
        get() = loaderCount == 1 && !hasCommon

    val singleLoaderName: String?
        get() = when {
            !isSingleLoader -> null
            hasFabric -> "fabric"
            hasForge -> "forge"
            hasNeoForge -> "neoforge"
            hasLegacyForge -> "legacyforge"
            else -> null
        }
}
