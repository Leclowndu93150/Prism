package dev.prism.gradle.dsl

open class FabricConfiguration : LoaderConfiguration {
    override val loaderName = "fabric"
    override val loaderDisplayName = "Fabric"
    var loaderVersion: String = ""
    var apiVersion: String? = null
    var yarnMappings: String? = null
    var enableDatagen: Boolean = false

    fun fabricApi(version: String) {
        apiVersion = version
    }

    fun datagen() {
        enableDatagen = true
    }
}
