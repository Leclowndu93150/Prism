package dev.prism.gradle.dsl

open class SharedCommonConfiguration {
    internal var hasMixin = false
    internal var hasMixinExtras = false
    internal val deps = DependencyBlock()

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
}
