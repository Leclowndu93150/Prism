package dev.prism.gradle.dsl

open class NeoForgeConfiguration : LoaderConfiguration {
    override val loaderName = "neoforge"
    override val loaderDisplayName = "Neoforge"
    var loaderVersion: String = ""
    var loaderVersionRange: String? = null
}
