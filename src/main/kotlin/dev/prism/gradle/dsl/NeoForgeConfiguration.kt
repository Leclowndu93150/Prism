package dev.prism.gradle.dsl

import org.gradle.api.Action

open class NeoForgeConfiguration : LoaderConfiguration {
    override val loaderName = "neoforge"
    override val loaderDisplayName = "Neoforge"
    var loaderVersion: String = ""
    var loaderVersionRange: String? = null
    internal val deps = DependencyBlock()
    internal val extraRuns = RunsBlock()

    fun dependencies(action: Action<DependencyBlock>) {
        action.execute(deps)
    }

    fun runs(action: Action<RunsBlock>) {
        action.execute(extraRuns)
    }
}
