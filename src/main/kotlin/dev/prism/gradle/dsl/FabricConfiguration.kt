package dev.prism.gradle.dsl

import org.gradle.api.Action

open class FabricConfiguration : LoaderConfiguration {
    override val loaderName = "fabric"
    override val loaderDisplayName = "Fabric"
    var loaderVersion: String = ""
    var apiVersion: String? = null
    var yarnMappings: String? = null
    var enableDatagen: Boolean = false
    internal val deps = DependencyBlock()
    internal val extraRuns = RunsBlock()
    internal val pubDeps = PublishingDepsBlock()

    fun fabricApi(version: String) {
        apiVersion = version
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
}
