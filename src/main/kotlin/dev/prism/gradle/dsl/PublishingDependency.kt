package dev.prism.gradle.dsl

import org.gradle.api.Action

enum class PublishingDepType {
    REQUIRED, OPTIONAL, INCOMPATIBLE, EMBEDDED
}

data class PublishingDep(
    val slug: String,
    val type: PublishingDepType,
    val platform: PublishingPlatform,
)

enum class PublishingPlatform {
    CURSEFORGE, MODRINTH, BOTH
}

open class PublishingDepsBlock {
    internal val deps = mutableListOf<PublishingDep>()

    fun requires(slug: String, platform: PublishingPlatform = PublishingPlatform.BOTH) {
        deps.add(PublishingDep(slug, PublishingDepType.REQUIRED, platform))
    }

    fun optional(slug: String, platform: PublishingPlatform = PublishingPlatform.BOTH) {
        deps.add(PublishingDep(slug, PublishingDepType.OPTIONAL, platform))
    }

    fun incompatible(slug: String, platform: PublishingPlatform = PublishingPlatform.BOTH) {
        deps.add(PublishingDep(slug, PublishingDepType.INCOMPATIBLE, platform))
    }

    fun embeds(slug: String, platform: PublishingPlatform = PublishingPlatform.BOTH) {
        deps.add(PublishingDep(slug, PublishingDepType.EMBEDDED, platform))
    }

    fun curseforge(action: Action<PlatformPublishingDeps>) {
        val block = PlatformPublishingDeps(PublishingPlatform.CURSEFORGE)
        action.execute(block)
        deps.addAll(block.deps)
    }

    fun modrinth(action: Action<PlatformPublishingDeps>) {
        val block = PlatformPublishingDeps(PublishingPlatform.MODRINTH)
        action.execute(block)
        deps.addAll(block.deps)
    }
}

open class PlatformPublishingDeps(private val platform: PublishingPlatform) {
    internal val deps = mutableListOf<PublishingDep>()

    fun requires(slug: String) { deps.add(PublishingDep(slug, PublishingDepType.REQUIRED, platform)) }
    fun optional(slug: String) { deps.add(PublishingDep(slug, PublishingDepType.OPTIONAL, platform)) }
    fun incompatible(slug: String) { deps.add(PublishingDep(slug, PublishingDepType.INCOMPATIBLE, platform)) }
    fun embeds(slug: String) { deps.add(PublishingDep(slug, PublishingDepType.EMBEDDED, platform)) }
}
