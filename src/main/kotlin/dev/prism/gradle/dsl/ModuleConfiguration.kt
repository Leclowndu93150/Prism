package dev.prism.gradle.dsl

import org.gradle.api.Action
import org.gradle.api.Project

open class ModuleConfiguration(val moduleName: String, private val project: Project) {
    val metadata = MetadataExtension()
    internal val versions = mutableMapOf<String, VersionConfiguration>()
    internal val publishingConfig = PublishingConfiguration()
    internal var kotlinVersion: String? = null
    internal val moduleDependencies = mutableListOf<String>()

    fun metadata(action: Action<MetadataExtension>) {
        action.execute(metadata)
    }

    fun kotlin(version: String = "2.1.20") {
        kotlinVersion = version
    }

    fun version(mcVersion: String, action: Action<VersionConfiguration>) {
        val config = versions.getOrPut(mcVersion) { VersionConfiguration(mcVersion) }
        action.execute(config)
    }

    fun dependsOn(vararg moduleNames: String) {
        moduleDependencies.addAll(moduleNames)
    }

    fun publishing(action: Action<PublishingConfiguration>) {
        action.execute(publishingConfig)
    }

    val providers get() = project.providers
}

class SettingsModuleConfig(val moduleName: String) {
    internal val versions = mutableMapOf<String, SettingsVersionConfig>()

    fun version(mcVersion: String, action: Action<SettingsVersionConfig>) {
        val config = versions.getOrPut(mcVersion) { SettingsVersionConfig(mcVersion) }
        action.execute(config)
    }
}
