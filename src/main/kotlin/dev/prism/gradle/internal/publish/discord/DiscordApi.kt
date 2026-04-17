// MIT License
// Copyright (c) 2023 modmuss50 - https://github.com/modmuss50/mod-publish-plugin
// Vendored into Prism.
package dev.prism.gradle.internal.publish.discord

import dev.prism.gradle.internal.publish.HttpUtils
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.net.http.HttpRequest

object DiscordApi {
    @OptIn(ExperimentalSerializationApi::class)
    val json = Json { explicitNulls = false; classDiscriminator = "class"; encodeDefaults = true }
    private val httpUtils = HttpUtils()
    private val headers: Map<String, String> = mapOf("Content-Type" to "application/json")

    fun executeWebhook(url: String, webhook: Webhook) {
        val body = HttpRequest.BodyPublishers.ofString(json.encodeToString(webhook))
        httpUtils.post<String>(url, body, headers)
    }

    @Serializable
    data class Webhook(
        val content: String? = null,
        val username: String? = null,
        @SerialName("avatar_url") val avatarUrl: String? = null,
        val tts: Boolean? = null,
        val embeds: List<Embed>? = null,
        val components: List<Component>? = null,
        val flags: Int? = null,
        @SerialName("thread_name") val threadName: String? = null,
    )

    @Serializable
    sealed class Component {
        protected abstract val type: Int
    }

    @Serializable
    data class ActionRow(val components: List<Component>? = null) : Component() {
        override val type: Int = 1
    }

    @Serializable
    data class ButtonComponent(val label: String? = null, val url: String? = null) : Component() {
        override val type: Int = 2
        val style: Int = 5
    }

    @Serializable
    data class Embed(
        val title: String? = null,
        val type: String? = null,
        val description: String? = null,
        val url: String? = null,
        val timestamp: String? = null,
        val color: Int? = null,
        val footer: EmbedFooter? = null,
        val image: EmbedImage? = null,
        val thumbnail: EmbedThumbnail? = null,
        val author: EmbedAuthor? = null,
        val fields: List<EmbedField>? = null,
    )

    @Serializable
    data class EmbedFooter(val text: String, @SerialName("icon_url") val iconUrl: String? = null)

    @Serializable
    data class EmbedImage(val url: String, val height: Int? = null, val width: Int? = null)

    @Serializable
    data class EmbedThumbnail(val url: String, val height: Int? = null, val width: Int? = null)

    @Serializable
    data class EmbedAuthor(val name: String, val url: String? = null, @SerialName("icon_url") val iconUrl: String? = null)

    @Serializable
    data class EmbedField(val name: String, val value: String, val inline: Boolean? = null)

    fun getWebhook(url: String): WebhookData = httpUtils.get(url, headers)

    @Serializable
    data class WebhookData(@SerialName("application_id") val applicationId: String?)
}
