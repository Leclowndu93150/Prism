package dev.prism.gradle.internal.publish.discord

import dev.prism.gradle.internal.publish.PublishResultsHolder
import org.gradle.api.DefaultTask
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.TaskAction

abstract class AnnounceDiscordTask : DefaultTask() {
    @get:Input abstract val webhookUrl: Property<String>
    @get:Input @get:Optional abstract val username: Property<String>
    @get:Input @get:Optional abstract val avatarUrl: Property<String>
    @get:Input @get:Optional abstract val content: Property<String>
    @get:Input @get:Optional abstract val embedTitle: Property<String>
    @get:Input @get:Optional abstract val embedDescription: Property<String>
    @get:Input @get:Optional abstract val embedColor: Property<Int>
    @get:Input abstract val includeProjectLinks: Property<Boolean>
    @get:Input @get:Optional abstract val dryRun: Property<Boolean>

    init {
        group = null
        description = "Posts a Discord webhook message after publishing finishes"
    }

    @TaskAction
    fun announce() {
        val url = webhookUrl.orNull ?: return
        if (url.isBlank()) return

        val results = PublishResultsHolder.collect(project)
        val fields = if (includeProjectLinks.getOrElse(true)) {
            results.map { DiscordApi.EmbedField(it.platform, it.url, false) }
        } else emptyList()

        val embed = DiscordApi.Embed(
            title = embedTitle.orNull,
            description = embedDescription.orNull,
            color = embedColor.orNull,
            fields = fields.takeIf { it.isNotEmpty() },
        )

        val webhook = DiscordApi.Webhook(
            content = content.orNull,
            username = username.orNull,
            avatarUrl = avatarUrl.orNull,
            embeds = if (embed.title != null || embed.description != null || !fields.isEmpty()) listOf(embed) else null,
        )

        if (dryRun.getOrElse(false)) {
            logger.lifecycle("Prism [dry-run] Discord webhook")
            logger.lifecycle("  url=${url.take(50)}...  results=${results.size}")
            return
        }

        DiscordApi.executeWebhook(url, webhook)
        logger.lifecycle("Prism: Posted Discord announcement (${results.size} results)")
    }
}
