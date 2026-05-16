package dev.prism.gradle.dsl

import org.gradle.api.Action
import org.gradle.api.Project

open class VersionConfiguration(val minecraftVersion: String) {
    /**
     * Java bytecode target (`javac --release`) for the emitted class files. This is what
     * end users need to run the mod. If unset, Prism picks a sensible default for the
     * Minecraft version (17 for 1.18-1.20, 21 for 1.21, 25 for 26+).
     */
    var javaVersion: Int? = null

    /**
     * JDK used to *run* the compiler (the toolchain). Defaults to whichever is higher of
     * [javaVersion] and the auto-detected minimum, so by default compileJdk == javaVersion.
     *
     * Set this if you need a newer JDK to read a dependency (e.g. a library compiled with
     * Java 21) while still emitting older bytecode for backwards compatibility.
     */
    var compileJdk: Int? = null

    var neoFormVersion: String? = null
    var parchmentMinecraftVersion: String? = null
    var parchmentMappingsVersion: String? = null
    var kotlinVersion: String? = null
    var minecraftVersionRange: List<String>? = null
    var unifiedAccessWidener: String? = null
    var changelog: String? = null
    var changelogFile: String? = null
    var obfuscateEnabled: Boolean = false
    val obfuscateOptions: ObfuscationOptions = ObfuscationOptions()

    fun obfuscate() {
        obfuscateEnabled = true
    }

    fun obfuscate(action: Action<ObfuscationOptions>) {
        obfuscateEnabled = true
        action.execute(obfuscateOptions)
    }

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

    /** JDK used to compile (toolchain). Always >= [resolvedJavaVersion]. */
    val resolvedCompileJdk: Int
        get() = maxOf(compileJdk ?: resolvedJavaVersion, resolvedJavaVersion)

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
