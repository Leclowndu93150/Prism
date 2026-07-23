package dev.prism.gradle.dsl

import org.gradle.api.Action
import org.gradle.api.Project
import org.gradle.api.provider.Provider

open class PrismExtension(private val project: Project) {
    val metadata = MetadataExtension()

    var version: String
        get() = metadata.version
        set(value) {
            metadata.version = value
        }

    var group: String
        get() = metadata.group
        set(value) {
            metadata.group = value
        }

    internal val versions = mutableMapOf<String, VersionConfiguration>()
    internal val publishingConfig = PublishingConfiguration()
    internal val extraRepositories = mutableListOf<RepositoryEntry>()
    internal var sharedCommonEnabled = false
    internal var globalKotlinVersion: String? = null
    internal val sharedCommonConfig = SharedCommonConfiguration()
    internal val modules = mutableMapOf<String, ModuleConfiguration>()
    internal var obfuscateEnabled: Boolean = false
    internal val obfuscateOptions: ObfuscationOptions = ObfuscationOptions()

    fun obfuscate() {
        obfuscateEnabled = true
    }

    fun obfuscate(action: Action<ObfuscationOptions>) {
        obfuscateEnabled = true
        action.execute(obfuscateOptions)
    }

    fun metadata(action: Action<MetadataExtension>) {
        action.execute(metadata)
    }

    fun kotlin(version: String = "2.1.20") {
        globalKotlinVersion = version
    }

    fun sharedCommon(action: Action<SharedCommonConfiguration>) {
        action.execute(sharedCommonConfig)
    }

    fun version(mcVersion: String, action: Action<VersionConfiguration>) {
        val config = versions.getOrPut(mcVersion) { VersionConfiguration(mcVersion) }
        action.execute(config)
    }

    fun publishing(action: Action<PublishingConfiguration>) {
        action.execute(publishingConfig)
    }

    fun mod(moduleName: String, action: Action<ModuleConfiguration>) {
        val config = modules.getOrPut(moduleName) { ModuleConfiguration(moduleName, project) }
        action.execute(config)
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

    fun ivy(name: String, url: String, artifactPattern: String) {
        extraRepositories.add(RepositoryEntry(name, url, artifactPattern))
    }

    fun gitCommit(short: Boolean = true): Provider<String> {
        val args = if (short) listOf("git", "rev-parse", "--short", "HEAD") else listOf("git", "rev-parse", "HEAD")
        return project.providers.exec { spec ->
            spec.commandLine(args)
            spec.isIgnoreExitValue = true
        }.standardOutput.asText.map { it.trim().ifEmpty { "unknown" } }
    }

    val providers get() = project.providers
}

data class RepositoryEntry(val name: String, val url: String, val artifactPattern: String? = null)
