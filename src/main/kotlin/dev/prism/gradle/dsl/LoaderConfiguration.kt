package dev.prism.gradle.dsl

import org.gradle.api.Action

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

    fun obfuscate(action: Action<ObfuscationOptions>) {
        obfuscateEnabled = true
        action.execute(obfuscateOptions)
    }
}
