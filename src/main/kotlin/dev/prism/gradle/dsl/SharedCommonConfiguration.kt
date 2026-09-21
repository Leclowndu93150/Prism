package dev.prism.gradle.dsl

open class SharedCommonConfiguration {
    internal var hasMixin = false
    internal var hasMixinExtras = false
    internal val deps = DependencyBlock()
    internal val rawProjectActions = mutableListOf<org.gradle.api.Action<org.gradle.api.Project>>()
    internal var minecraftLibrariesEnabled = false
    internal var minecraftLibrariesVersion: String? = null
    internal val perTargetSourceDirs = mutableListOf<String>()
    internal var junitVersion: String? = null

    var javaVersion: Int? = null

    fun mixin() {
        hasMixin = true
    }

    fun mixinExtras() {
        hasMixin = true
        hasMixinExtras = true
    }

    fun minecraftLibraries(minecraftVersion: String? = null) {
        minecraftLibrariesEnabled = true
        minecraftLibrariesVersion = minecraftVersion
    }

    fun perTargetSources(vararg paths: String) {
        perTargetSourceDirs.addAll(paths)
    }

    fun tests(junitVersion: String = "5.14.4") {
        this.junitVersion = junitVersion
    }

    fun dependencies(action: org.gradle.api.Action<DependencyBlock>) {
        action.execute(deps)
    }

    fun rawProject(action: org.gradle.api.Action<org.gradle.api.Project>) {
        rawProjectActions.add(action)
    }
}
