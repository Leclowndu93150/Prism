package dev.prism.gradle.dsl

open class SharedCommonConfiguration {
    internal var hasMixin = false
    internal var hasMixinExtras = false
    internal val deps = DependencyBlock()
    internal val rawProjectActions = mutableListOf<org.gradle.api.Action<org.gradle.api.Project>>()

    fun mixin() {
        hasMixin = true
    }

    fun mixinExtras() {
        hasMixin = true
        hasMixinExtras = true
    }

    fun dependencies(action: org.gradle.api.Action<DependencyBlock>) {
        action.execute(deps)
    }

    fun rawProject(action: org.gradle.api.Action<org.gradle.api.Project>) {
        rawProjectActions.add(action)
    }
}
