package dev.prism.gradle.dsl

import org.gradle.api.Action
import org.gradle.api.provider.Provider

open class PublishingConfiguration {
    var changelog: String? = null
    var changelogFile: String? = null
    var type: ReleaseType = ReleaseType.STABLE

    internal var curseforgeConfig: CurseForgeConfig? = null
    internal var modrinthConfig: ModrinthConfig? = null

    val isConfigured: Boolean
        get() = curseforgeConfig != null || modrinthConfig != null

    fun curseforge(action: Action<CurseForgeConfig>) {
        if (curseforgeConfig == null) curseforgeConfig = CurseForgeConfig()
        action.execute(curseforgeConfig!!)
    }

    fun modrinth(action: Action<ModrinthConfig>) {
        if (modrinthConfig == null) modrinthConfig = ModrinthConfig()
        action.execute(modrinthConfig!!)
    }
}

enum class ReleaseType {
    STABLE, BETA, ALPHA
}

open class CurseForgeConfig {
    var accessToken: Provider<String>? = null
    var projectId: String = ""
}

open class ModrinthConfig {
    var accessToken: Provider<String>? = null
    var projectId: String = ""
}
