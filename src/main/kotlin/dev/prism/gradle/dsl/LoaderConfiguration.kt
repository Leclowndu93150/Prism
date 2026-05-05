package dev.prism.gradle.dsl

interface LoaderConfiguration {
    val loaderName: String
    val loaderDisplayName: String
    val publishLoaderSlug: String
        get() = loaderName
    var changelog: String?
    var changelogFile: String?
    var obfuscateEnabled: Boolean
    val obfuscateOptions: ObfuscationOptions

    fun obfuscate() {
        obfuscateEnabled = true
    }

    fun obfuscate(action: org.gradle.api.Action<ObfuscationOptions>) {
        obfuscateEnabled = true
        action.execute(obfuscateOptions)
    }
}
