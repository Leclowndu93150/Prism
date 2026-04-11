package dev.prism.gradle.dsl

import org.gradle.api.Action
import org.gradle.api.Project

open class VersionConfiguration(val minecraftVersion: String) {
    var javaVersion: Int? = null
    var neoFormVersion: String? = null
    var parchmentMinecraftVersion: String? = null
    var parchmentMappingsVersion: String? = null
    var kotlinVersion: String? = null
    var minecraftVersionRange: List<String>? = null
    var unifiedAccessWidener: String? = null

    internal var fabricConfig: FabricConfiguration? = null
    internal var forgeConfig: ForgeConfiguration? = null
    internal var lexForgeConfig: LexForgeConfiguration? = null
    internal var neoForgeConfig: NeoForgeConfiguration? = null
    internal var legacyForgeConfig: LegacyForgeConfiguration? = null
    internal val commonDeps = DependencyBlock()
    internal val pubDeps = PublishingDepsBlock()
    internal val rawCommonProjectActions = mutableListOf<Action<Project>>()

    val resolvedJavaVersion: Int
        get() = javaVersion ?: detectJavaVersion(minecraftVersion)

    fun kotlin(version: String = "2.1.20") {
        kotlinVersion = version
    }

    fun common(action: Action<DependencyBlock>) {
        action.execute(commonDeps)
    }

    fun rawCommonProject(action: Action<Project>) {
        rawCommonProjectActions.add(action)
    }

    fun fabric(action: Action<FabricConfiguration>) {
        if (fabricConfig == null) fabricConfig = FabricConfiguration()
        action.execute(fabricConfig!!)
    }

    fun forge(action: Action<ForgeConfiguration>) {
        if (forgeConfig == null) forgeConfig = ForgeConfiguration()
        action.execute(forgeConfig!!)
    }

    fun lexForge(action: Action<LexForgeConfiguration>) {
        if (lexForgeConfig == null) lexForgeConfig = LexForgeConfiguration()
        action.execute(lexForgeConfig!!)
    }

    fun neoforge(action: Action<NeoForgeConfiguration>) {
        if (neoForgeConfig == null) neoForgeConfig = NeoForgeConfiguration()
        action.execute(neoForgeConfig!!)
    }

    fun legacyForge(action: Action<LegacyForgeConfiguration>) {
        if (legacyForgeConfig == null) legacyForgeConfig = LegacyForgeConfiguration()
        action.execute(legacyForgeConfig!!)
    }

    fun accessWidener(path: String) {
        unifiedAccessWidener = path
    }

    fun minecraftVersions(vararg versions: String) {
        minecraftVersionRange = versions.toList()
    }

    fun publishingDependencies(action: Action<PublishingDepsBlock>) {
        action.execute(pubDeps)
    }

    val loaders: List<LoaderConfiguration>
        get() = listOfNotNull(fabricConfig, forgeConfig, lexForgeConfig, neoForgeConfig, legacyForgeConfig)

    companion object {
        fun detectJavaVersion(mcVersion: String): Int {
            val parts = mcVersion.split(".")
            val major = parts.getOrNull(0)?.toIntOrNull() ?: 1
            val minor = parts.getOrNull(1)?.toIntOrNull() ?: 0

            return when {
                major >= 26 -> 25
                major >= 2 || minor >= 21 -> 21
                minor >= 18 -> 17
                minor >= 17 -> 16
                else -> 8
            }
        }
    }
}
