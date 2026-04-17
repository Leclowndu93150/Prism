package dev.prism.gradle.dsl

import org.gradle.api.Action
import org.gradle.api.Project

open class LegacyForgeConfiguration : LoaderConfiguration {
    override val loaderName = "legacyforge"
    override val loaderDisplayName = "Forge"
    var mcVersion: String = "1.12.2"
    var forgeVersion: String = "14.23.5.2847"
    var mappingChannel: String = "stable"
    var mappingVersion: String = "39"
    var username: String = "Developer"
    var useModernJavaSyntax: Boolean = false
    var mixinBooter: Boolean = true
    var mixinBooterVersion: String = "10.7"
    internal val deps = DependencyBlock()
    internal val extraRuns = RunsBlock()
    internal val pubDeps = PublishingDepsBlock()
    internal val mixinTweakers = mutableListOf<String>()
    internal val accessTransformers = mutableListOf<String>()
    internal val rawProjectActions = mutableListOf<Action<Project>>()
    internal val extraConfigurations = mutableSetOf<String>()
    internal val mixinOptions = MixinOptions()
    internal var coreModClass: String? = null

    fun mixin(refmap: String = "") {
        mixinTweakers.add("org.spongepowered.asm.launch.MixinTweaker")
    }

    fun mixins(action: Action<MixinOptions>) {
        action.execute(mixinOptions)
    }

    fun coreMod(fqn: String) {
        coreModClass = fqn
    }

    fun accessTransformer(path: String) {
        accessTransformers.add(path)
    }

    fun dependencies(action: Action<DependencyBlock>) {
        action.execute(deps)
    }

    fun runs(action: Action<RunsBlock>) {
        action.execute(extraRuns)
    }

    fun publishingDependencies(action: Action<PublishingDepsBlock>) {
        action.execute(pubDeps)
    }

    fun rawProject(action: Action<Project>) {
        rawProjectActions.add(action)
    }

    fun configuration(name: String) {
        extraConfigurations.add(name)
    }
}
