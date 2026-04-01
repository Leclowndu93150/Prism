package dev.prism.gradle.dsl

import org.gradle.api.Action

open class VersionConfiguration(val minecraftVersion: String) {
    var javaVersion: Int? = null
    var neoFormVersion: String? = null
    var parchmentMinecraftVersion: String? = null
    var parchmentMappingsVersion: String? = null
    var kotlinVersion: String? = null
    var minecraftVersionRange: List<String>? = null

    internal var fabricConfig: FabricConfiguration? = null
    internal var forgeConfig: ForgeConfiguration? = null
    internal var neoForgeConfig: NeoForgeConfiguration? = null
    internal val commonDeps = DependencyBlock()

    val resolvedJavaVersion: Int
        get() = javaVersion ?: detectJavaVersion(minecraftVersion)

    fun kotlin(version: String = "2.1.20") {
        kotlinVersion = version
    }

    fun common(action: Action<DependencyBlock>) {
        action.execute(commonDeps)
    }

    fun fabric(action: Action<FabricConfiguration>) {
        if (fabricConfig == null) fabricConfig = FabricConfiguration()
        action.execute(fabricConfig!!)
    }

    fun forge(action: Action<ForgeConfiguration>) {
        if (forgeConfig == null) forgeConfig = ForgeConfiguration()
        action.execute(forgeConfig!!)
    }

    fun neoforge(action: Action<NeoForgeConfiguration>) {
        if (neoForgeConfig == null) neoForgeConfig = NeoForgeConfiguration()
        action.execute(neoForgeConfig!!)
    }

    fun minecraftVersions(vararg versions: String) {
        minecraftVersionRange = versions.toList()
    }

    val loaders: List<LoaderConfiguration>
        get() = listOfNotNull(fabricConfig, forgeConfig, neoForgeConfig)

    companion object {
        fun detectJavaVersion(mcVersion: String): Int {
            val parts = mcVersion.split(".")
            val major = parts.getOrNull(0)?.toIntOrNull() ?: 1
            val minor = parts.getOrNull(1)?.toIntOrNull() ?: 0

            return when {
                major >= 2 || minor >= 21 -> 21
                minor >= 18 -> 17
                minor >= 17 -> 16
                else -> 8
            }
        }
    }
}
