package dev.prism.gradle.dsl

import org.gradle.api.Action
import org.gradle.api.Project
import net.neoforged.moddevgradle.legacyforge.dsl.LegacyForgeExtension

open class ForgeConfiguration : LoaderConfiguration {
    override val loaderName = "forge"
    override val loaderDisplayName = "Forge"
    var loaderVersion: String = ""
    var loaderVersionRange: String? = null
    override var changelog: String? = null
    override var changelogFile: String? = null
    internal val deps = DependencyBlock()
    internal val extraRuns = RunsBlock()
    internal val pubDeps = PublishingDepsBlock()
    internal val mixinOptions = MixinOptions()
    internal val rawProjectActions = mutableListOf<Action<Project>>()
    internal val rawLegacyForgeActions = mutableListOf<Action<LegacyForgeExtension>>()
    internal val extraConfigurations = mutableSetOf<String>()
    internal val remapConfigurations = mutableSetOf<String>()

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

    fun rawLegacyForge(action: Action<LegacyForgeExtension>) {
        rawLegacyForgeActions.add(action)
    }

    fun configuration(name: String) {
        extraConfigurations.add(name)
    }

    fun remapConfiguration(name: String) {
        extraConfigurations.add(name)
        remapConfigurations.add(name)
    }
}
