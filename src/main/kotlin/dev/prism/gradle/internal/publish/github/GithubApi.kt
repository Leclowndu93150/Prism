// MIT License
// Copyright (c) 2023 modmuss50 - https://github.com/modmuss50/mod-publish-plugin
// Vendored into Prism.
package dev.prism.gradle.internal.publish.github

import dev.prism.gradle.internal.publish.HttpUtils
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import java.io.File
import java.net.http.HttpRequest

class GithubApi(private val accessToken: String, private val apiEndpoint: String = "https://api.github.com") {
    private val httpUtils = HttpUtils()

    @Serializable
    data class Repository(@SerialName("full_name") val fullName: String)

    @Serializable
    data class Release(
        val id: Long,
        @SerialName("html_url") val htmlUrl: String,
        @SerialName("upload_url") val uploadUrl: String,
    )

    @Serializable
    data class CreateReleaseRequest(
        @SerialName("tag_name") val tagName: String,
        @SerialName("target_commitish") val targetCommitish: String,
        val name: String,
        val body: String,
        val draft: Boolean,
        val prerelease: Boolean,
    )

    @Serializable
    data class UpdateReleaseRequest(val draft: Boolean)

    @Serializable
    class Asset

    private val headers: Map<String, String>
        get() = mapOf(
            "Authorization" to "token $accessToken",
            "Accept" to "application/vnd.github+json",
            "X-GitHub-Api-Version" to "2022-11-28",
        )

    fun getRepository(repository: String): Repository =
        httpUtils.get("$apiEndpoint/repos/$repository", headers)

    fun createRelease(repository: String, request: CreateReleaseRequest): Release {
        val body = HttpRequest.BodyPublishers.ofString(httpUtils.json.encodeToString(request))
        return httpUtils.post("$apiEndpoint/repos/$repository/releases", body, headers + ("Content-Type" to "application/json"))
    }

    fun getRelease(repository: String, releaseId: Long): Release =
        httpUtils.get("$apiEndpoint/repos/$repository/releases/$releaseId", headers)

    fun updateRelease(repository: String, releaseId: Long, request: UpdateReleaseRequest): Release {
        val body = HttpRequest.BodyPublishers.ofString(httpUtils.json.encodeToString(request))
        return httpUtils.patch("$apiEndpoint/repos/$repository/releases/$releaseId", body, headers + ("Content-Type" to "application/json"))
    }

    fun uploadAsset(release: Release, file: File) {
        val uploadUrl = release.uploadUrl.substringBefore("{")
        val url = "$uploadUrl?name=${file.name}"
        val body = HttpRequest.BodyPublishers.ofFile(file.toPath())
        httpUtils.post<Asset>(url, body, headers + ("Content-Type" to "application/java-archive"))
    }
}
