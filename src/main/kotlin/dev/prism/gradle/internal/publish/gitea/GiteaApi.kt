// MIT License
// Copyright (c) 2023 modmuss50 - https://github.com/modmuss50/mod-publish-plugin
// Vendored into Prism.
package dev.prism.gradle.internal.publish.gitea

import dev.prism.gradle.internal.publish.HttpUtils
import dev.prism.gradle.internal.publish.MultipartBodyBuilder
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.net.http.HttpRequest
import java.net.http.HttpResponse

class GiteaApi(private val accessToken: String, private val baseUrl: String, private val repository: String) {
    private val httpUtils = HttpUtils(exceptionFactory = GiteaHttpExceptionFactory())

    @Serializable
    data class Release(
        val id: Long,
        @SerialName("html_url") val htmlUrl: String,
        @SerialName("upload_url") val uploadUrl: String,
    )

    @Serializable
    data class CreateRelease(
        val body: String? = null,
        val draft: Boolean,
        val name: String? = null,
        val prerelease: Boolean,
        @SerialName("tag_name") val tagName: String,
        val targetCommitish: String,
    )

    @Serializable
    data class ErrorResponse(val message: String, val url: String)

    private val headers: Map<String, String>
        get() = mapOf(
            "Authorization" to "token $accessToken",
            "Content-Type" to "application/json",
        )

    fun createRelease(metadata: CreateRelease): Release {
        val body = HttpRequest.BodyPublishers.ofString(Json.encodeToString(metadata))
        return httpUtils.post("$baseUrl/repos/$repository/releases", body, headers)
    }

    fun getRelease(id: Long): Release =
        httpUtils.get("$baseUrl/repos/$repository/releases/$id", headers)

    fun uploadAsset(release: Release, file: File) {
        val bodyBuilder = MultipartBodyBuilder()
            .addFormDataPart("attachment", file.name, file, "application/java-archive")
        val multipartHeaders = headers.toMutableMap()
        multipartHeaders["Content-Type"] = bodyBuilder.getContentType()
        httpUtils.post<String>(release.uploadUrl, bodyBuilder.build(), multipartHeaders)
    }

    fun publishRelease(release: Release) {
        val body = HttpRequest.BodyPublishers.ofString("""{"draft":false}""")
        httpUtils.patch<String>("$baseUrl/repos/$repository/releases/${release.id}", body, headers)
    }

    private class GiteaHttpExceptionFactory : HttpUtils.HttpExceptionFactory {
        val json = Json { ignoreUnknownKeys = true }
        override fun createException(response: HttpResponse<String>): HttpUtils.HttpException {
            return try {
                val errorResponse = json.decodeFromString<ErrorResponse>(response.body())
                HttpUtils.HttpException(response, errorResponse.message)
            } catch (e: SerializationException) {
                HttpUtils.HttpException(response, "Unknown error")
            }
        }
    }
}
