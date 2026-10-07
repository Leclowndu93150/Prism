package dev.prism.gradle.dsl

import dev.prism.gradle.internal.PrismLayout
import org.gradle.api.Action
import org.gradle.api.initialization.Settings
import java.io.File

class PrismSettingsExtension(private val settings: Settings) {
    internal val versions = mutableMapOf<String, SettingsVersionConfig>()
    internal val modules = mutableMapOf<String, SettingsModuleConfig>()
    internal var hasSharedCommon = false
    internal var sharedCommonPath: String = "common"
    internal var rootVersion: String? = null

    fun sharedCommon() {
        sharedCommon("common")
    }

    fun sharedCommon(path: String) {
        if (hasSharedCommon) return
        require(rootVersion == null) {
            "Prism: sharedCommon() cannot be combined with atRoot(). A root layout has a single version, so there is nothing to share."
        }
        hasSharedCommon = true
        sharedCommonPath = path
        settings.include(":common")
        settings.project(":common").projectDir = File(settings.settingsDir, path)
    }

    fun version(mcVersion: String, action: Action<SettingsVersionConfig>) {
        val config = versions.getOrPut(mcVersion) { SettingsVersionConfig(mcVersion) }
        action.execute(config)

        if (config.isAtRoot) {
            registerRootVersion(mcVersion, config)
            return
        }
        require(rootVersion == null) {
            "Prism: version '$mcVersion' cannot be added because version '$rootVersion' uses atRoot(). A root layout holds exactly one version."
        }

        if (config.isSingleLoader) {
            registerSingleProject(mcVersion, config.singleLoaderName!!)
        } else {
            registerSubproject(mcVersion, "common")
            if (config.hasFabric) registerSubproject(mcVersion, "fabric")
            if (config.hasForge) registerSubproject(mcVersion, "forge")
            if (config.hasLexForge) registerSubproject(mcVersion, "lexforge")
            if (config.hasForge16) registerSubproject(mcVersion, "forge16")
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
                if (versionConfig.hasLexForge) registerModuleSubproject(moduleName, mcVersion, "lexforge")
                if (versionConfig.hasForge16) registerModuleSubproject(moduleName, mcVersion, "forge16")
                if (versionConfig.hasNeoForge) registerModuleSubproject(moduleName, mcVersion, "neoforge")
                if (versionConfig.hasLegacyForge) registerModuleSubproject(moduleName, mcVersion, "legacyforge")
            }
        }
    }

    private fun registerRootVersion(mcVersion: String, config: SettingsVersionConfig) {
        require(config.isSingleLoader) {
            "Prism: atRoot() needs exactly one loader and no common() in version '$mcVersion'."
        }
        require(rootVersion == null || rootVersion == mcVersion) {
            "Prism: atRoot() is already used by version '$rootVersion'. Only one version can live at the root."
        }
        require(versions.keys.all { it == mcVersion }) {
            "Prism: atRoot() on version '$mcVersion' needs it to be the only version, but ${versions.keys.filter { it != mcVersion }} are also declared."
        }
        require(!hasSharedCommon) {
            "Prism: atRoot() cannot be combined with sharedCommon()."
        }
        rootVersion = mcVersion
        PrismLayout.markRootVersion(settings.gradle, mcVersion)
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
    var hasLexForge = false; private set
    var hasForge16 = false; private set
    var hasNeoForge = false; private set
    var hasLegacyForge = false; private set
    var hasCommon = false; private set
    var isAtRoot = false; private set

    fun common() { hasCommon = true }
    fun fabric() { hasFabric = true }
    fun forge() { hasForge = true }
    fun lexForge() { hasLexForge = true }
    fun forge16() { hasForge16 = true }
    fun neoforge() { hasNeoForge = true }
    fun legacyForge() { hasLegacyForge = true }
    fun atRoot() { isAtRoot = true }

    val loaderCount: Int
        get() = listOf(hasFabric, hasForge, hasLexForge, hasForge16, hasNeoForge, hasLegacyForge).count { it }

    val isSingleLoader: Boolean
        get() = loaderCount == 1 && !hasCommon

    val singleLoaderName: String?
        get() = when {
            !isSingleLoader -> null
            hasFabric -> "fabric"
            hasForge -> "forge"
            hasLexForge -> "lexforge"
            hasForge16 -> "forge16"
            hasNeoForge -> "neoforge"
            hasLegacyForge -> "legacyforge"
            else -> null
        }
}
