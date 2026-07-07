package dev.prism.gradle.dsl

import org.gradle.api.Action
import org.gradle.api.Project

open class Forge16Configuration : LoaderConfiguration {
    override val loaderName = "forge16"
    override val loaderDisplayName = "Forge"
    override val publishLoaderSlug = "forge"
    var loaderVersion: String = ""
    var loaderVersionRange: String? = null
    var mappingsChannel: String = "snapshot"
    var mappingsVersion: String = "20210309-1.16.5"
    override var changelog: String? = null
    override var changelogFile: String? = null
    override var obfuscateEnabled: Boolean = false
    override val obfuscateOptions: ObfuscationOptions = ObfuscationOptions()
    internal val deps = DependencyBlock()
    internal val extraRuns = RunsBlock()
    internal val pubDeps = PublishingDepsBlock()
    internal val mixinOptions = MixinOptions()
    internal val accessTransformers = mutableListOf<String>()
    internal val rawProjectActions = mutableListOf<Action<Project>>()
    internal val extraConfigurations = mutableSetOf<String>()

    fun dependencies(action: Action<DependencyBlock>) {
        action.execute(deps)
    }

    fun runs(action: Action<RunsBlock>) {
        action.execute(extraRuns)
    }

    fun publishingDependencies(action: Action<PublishingDepsBlock>) {
        action.execute(pubDeps)
    }

    fun mixins(action: Action<MixinOptions>) {
        action.execute(mixinOptions)
    }

    fun mappings(channel: String, version: String) {
        mappingsChannel = channel
        mappingsVersion = version
    }

    fun accessTransformer(path: String) {
        accessTransformers.add(path)
    }

    fun rawProject(action: Action<Project>) {
        rawProjectActions.add(action)
    }

    fun configuration(name: String) {
        extraConfigurations.add(name)
    }
}
