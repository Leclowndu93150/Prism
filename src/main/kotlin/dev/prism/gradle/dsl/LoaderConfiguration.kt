package dev.prism.gradle.dsl

interface LoaderConfiguration {
    val loaderName: String
    val loaderDisplayName: String
    val publishLoaderSlug: String
        get() = loaderName
    var changelog: String?
    var changelogFile: String?
}
