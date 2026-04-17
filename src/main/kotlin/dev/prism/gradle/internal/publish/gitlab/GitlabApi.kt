// MIT License
// Copyright (c) 2023 modmuss50 - https://github.com/modmuss50/mod-publish-plugin
// Vendored into Prism.
package dev.prism.gradle.internal.publish.gitlab

import dev.prism.gradle.internal.publish.HttpUtils
import dev.prism.gradle.internal.publish.MultipartBodyBuilder
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.net.URLEncoder
import java.net.http.HttpRequest
import kotlin.text.Charsets

class GitlabApi(
    private val accessToken: String,
    private val apiEndpoint: String = "https://gitlab.com/api/v4",
) {
    private val httpUtils = HttpUtils()

    @OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
    private val gitlabJson = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    @Serializable
    data class Release(
        @SerialName("tag_name") val tagName: String,
        val name: String,
        val description: String,
        val assets: Assets? = null,
    )

    @Serializable
    data class Assets(val links: List<AssetLink> = emptyList())

    @Serializable
    data class AssetLink(
        val name: String,
        val url: String,
        @SerialName("link_type") val linkType: String,
    )

    @Serializable
    data class CreateReleaseRequest(
        val name: String,
        @SerialName("tag_name") val tagName: String,
        val description: String,
        val ref: String,
    )

    @Serializable
    data class UpdateReleaseRequest(val name: String? = null, val description: String? = null)

    @Serializable
    data class UploadResponse(val id: Long, val alt: String, val url: String)

    private val headers: Map<String, String>
        get() = mapOf("PRIVATE-TOKEN" to accessToken)

    fun createRelease(projectId: Long, request: CreateReleaseRequest): Release {
        val body = HttpRequest.BodyPublishers.ofString(gitlabJson.encodeToString(request))
        return httpUtils.post("$apiEndpoint/projects/$projectId/releases", body, headers + ("Content-Type" to "application/json"))
    }

    fun getRelease(projectId: Long, tagName: String): Release {
        val encodedTag = URLEncoder.encode(tagName, Charsets.UTF_8)
        return httpUtils.get("$apiEndpoint/projects/$projectId/releases/$encodedTag", headers)
    }

    fun updateRelease(projectId: Long, tagName: String, request: UpdateReleaseRequest): Release {
        val encodedTag = URLEncoder.encode(tagName, Charsets.UTF_8)
        val body = HttpRequest.BodyPublishers.ofString(gitlabJson.encodeToString(request))
        return httpUtils.put("$apiEndpoint/projects/$projectId/releases/$encodedTag", body, headers + ("Content-Type" to "application/json"))
    }

    fun uploadAsset(projectId: Long, file: File): AssetLink {
        val builder = MultipartBodyBuilder().addFormDataPart("file", file.name, file)
        val response: UploadResponse = httpUtils.post(
            "$apiEndpoint/projects/$projectId/uploads",
            builder.build(),
            headers + ("Content-Type" to builder.getContentType()),
        )
        return AssetLink(name = file.name, url = response.url, linkType = "other")
    }
}
