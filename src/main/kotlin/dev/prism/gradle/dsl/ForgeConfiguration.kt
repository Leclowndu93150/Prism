package dev.prism.gradle.dsl

import org.gradle.api.Action

open class ForgeConfiguration : LoaderConfiguration {
    override val loaderName = "forge"
    override val loaderDisplayName = "Forge"
    var loaderVersion: String = ""
    var loaderVersionRange: String? = null
    internal val deps = DependencyBlock()

    fun dependencies(action: Action<DependencyBlock>) {
        action.execute(deps)
    }
}
