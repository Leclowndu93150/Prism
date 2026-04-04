package dev.prism.gradle.dsl

import org.gradle.api.Action
import org.gradle.api.Project

open class PrismExtension(private val project: Project) {
    val metadata = MetadataExtension()
    internal val versions = mutableMapOf<String, VersionConfiguration>()
    internal val publishingConfig = PublishingConfiguration()
    internal val extraRepositories = mutableListOf<RepositoryEntry>()
    internal var sharedCommonEnabled = false
    internal var globalKotlinVersion: String? = null

    fun metadata(action: Action<MetadataExtension>) {
        action.execute(metadata)
    }

    fun kotlin(version: String = "2.1.20") {
        globalKotlinVersion = version
    }

    fun version(mcVersion: String, action: Action<VersionConfiguration>) {
        val config = versions.getOrPut(mcVersion) { VersionConfiguration(mcVersion) }
        action.execute(config)
    }

    fun publishing(action: Action<PublishingConfiguration>) {
        action.execute(publishingConfig)
    }

    fun curseMaven() {
        extraRepositories.add(RepositoryEntry("CurseMaven", "https://cursemaven.com"))
    }

    fun modrinthMaven() {
        extraRepositories.add(RepositoryEntry("Modrinth Maven", "https://api.modrinth.com/maven"))
    }

    fun maven(name: String, url: String) {
        extraRepositories.add(RepositoryEntry(name, url))
    }

    val providers get() = project.providers
}

data class RepositoryEntry(val name: String, val url: String)
