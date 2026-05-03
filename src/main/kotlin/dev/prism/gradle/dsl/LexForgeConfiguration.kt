package dev.prism.gradle.dsl

import net.minecraftforge.gradle.MinecraftExtensionForProject
import org.gradle.api.Action
import org.gradle.api.Project

open class LexForgeConfiguration : LoaderConfiguration {
    override val loaderName = "lexforge"
    override val loaderDisplayName = "LexForge"
    override val publishLoaderSlug = "forge"
    var loaderVersion: String = ""
    var loaderVersionRange: String? = null
    var mappingsChannel: String? = null
    var mappingsVersion: String? = null
    override var changelog: String? = null
    override var changelogFile: String? = null
    internal val deps = DependencyBlock()
    internal val extraRuns = RunsBlock()
    internal val pubDeps = PublishingDepsBlock()
    internal val mixinOptions = MixinOptions()
    internal val rawProjectActions = mutableListOf<Action<Project>>()
    internal val rawMinecraftActions = mutableListOf<Action<MinecraftExtensionForProject>>()

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

    fun rawProject(action: Action<Project>) {
        rawProjectActions.add(action)
    }

    fun rawLexForge(action: Action<MinecraftExtensionForProject>) {
        rawMinecraftActions.add(action)
    }
}
