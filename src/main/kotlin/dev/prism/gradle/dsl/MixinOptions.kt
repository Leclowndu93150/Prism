package dev.prism.gradle.dsl

open class MixinOptions {
    internal var autoDetect = true
    internal val explicitConfigs = mutableListOf<String>()
    var refmapName: String? = null

    fun autoDetect(enabled: Boolean) {
        autoDetect = enabled
    }

    fun disableAutoDetect() {
        autoDetect = false
    }

    fun config(path: String) {
        explicitConfigs.add(path)
    }

    fun configs(vararg paths: String) {
        explicitConfigs.addAll(paths)
    }

    fun refmap(name: String) {
        refmapName = name
    }
}
