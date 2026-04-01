package dev.prism.gradle.dsl

import org.gradle.api.Action
import org.gradle.api.Project

open class PrismExtension(private val project: Project) {
    val metadata = MetadataExtension()
    internal val versions = mutableMapOf<String, VersionConfiguration>()
    internal val publishingConfig = PublishingConfiguration()

    fun metadata(action: Action<MetadataExtension>) {
        action.execute(metadata)
    }

    fun version(mcVersion: String, action: Action<VersionConfiguration>) {
        val config = versions.getOrPut(mcVersion) { VersionConfiguration(mcVersion) }
        action.execute(config)
    }

    fun publishing(action: Action<PublishingConfiguration>) {
        action.execute(publishingConfig)
    }

    val providers get() = project.providers
}
