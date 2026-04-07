package dev.prism.gradle.dsl

import org.gradle.api.Action
import org.gradle.api.Project
import net.fabricmc.loom.api.LoomGradleExtensionAPI

open class FabricConfiguration : LoaderConfiguration {
    override val loaderName = "fabric"
    override val loaderDisplayName = "Fabric"
    var loaderVersion: String = ""
    var apiVersion: String? = null
    var yarnVersion: String? = null
    var enableDatagen: Boolean = false
    internal val deps = DependencyBlock()
    internal val extraRuns = RunsBlock()
    internal val pubDeps = PublishingDepsBlock()
    internal val mixinOptions = MixinOptions()
    internal val rawProjectActions = mutableListOf<Action<Project>>()
    internal val rawLoomActions = mutableListOf<Action<LoomGradleExtensionAPI>>()
    internal val extraConfigurations = mutableSetOf<String>()

    fun fabricApi(version: String) {
        apiVersion = version
    }

    fun yarn(version: String) {
        yarnVersion = version
    }

    fun datagen() {
        enableDatagen = true
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

    fun mixins(action: Action<MixinOptions>) {
        action.execute(mixinOptions)
    }

    fun rawProject(action: Action<Project>) {
        rawProjectActions.add(action)
    }

    fun rawLoom(action: Action<LoomGradleExtensionAPI>) {
        rawLoomActions.add(action)
    }

    fun configuration(name: String) {
        extraConfigurations.add(name)
    }
}
