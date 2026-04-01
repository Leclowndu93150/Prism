package dev.prism.gradle.dsl

open class ForgeConfiguration : LoaderConfiguration {
    override val loaderName = "forge"
    override val loaderDisplayName = "Forge"
    var loaderVersion: String = ""
    var loaderVersionRange: String? = null
}
