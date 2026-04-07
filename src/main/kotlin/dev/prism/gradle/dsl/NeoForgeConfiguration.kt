package dev.prism.gradle.dsl

import org.gradle.api.Action
import org.gradle.api.Project
import net.neoforged.moddevgradle.dsl.NeoForgeExtension

open class NeoForgeConfiguration : LoaderConfiguration {
    override val loaderName = "neoforge"
    override val loaderDisplayName = "NeoForge"
    var loaderVersion: String = ""
    var loaderVersionRange: String? = null
    internal val deps = DependencyBlock()
    internal val extraRuns = RunsBlock()
    internal val pubDeps = PublishingDepsBlock()
    internal val mixinOptions = MixinOptions()
    internal val rawProjectActions = mutableListOf<Action<Project>>()
    internal val rawNeoForgeActions = mutableListOf<Action<NeoForgeExtension>>()
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

    fun rawProject(action: Action<Project>) {
        rawProjectActions.add(action)
    }

    fun rawNeoForge(action: Action<NeoForgeExtension>) {
        rawNeoForgeActions.add(action)
    }

    fun configuration(name: String) {
        extraConfigurations.add(name)
    }
}
