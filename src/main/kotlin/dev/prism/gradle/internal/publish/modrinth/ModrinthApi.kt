// MIT License
// Copyright (c) 2023 modmuss50 - https://github.com/modmuss50/mod-publish-plugin
// Vendored into Prism, PlatformDependency refs swapped for Prism's PublishingDepType.
package dev.prism.gradle.internal.publish.modrinth

import dev.prism.gradle.dsl.PublishingDepType
import dev.prism.gradle.dsl.ReleaseType as PrismReleaseType
import dev.prism.gradle.internal.publish.HttpUtils
import dev.prism.gradle.internal.publish.MultipartBodyBuilder
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Path
import java.time.Duration
import kotlin.io.path.name

// https://docs.modrinth.com/api-spec/#tag/versions/operation/createVersion
class ModrinthApi(private val accessToken: String, private val baseUrl: String) {
    private val httpUtils = HttpUtils(
        exceptionFactory = ModrinthHttpExceptionFactory(),
        timeout = Duration.ofSeconds(60),
    )

    @Serializable
    enum class VersionType {
        @SerialName("alpha") ALPHA,
        @SerialName("beta") BETA,
        @SerialName("release") RELEASE,
        ;
        companion object {
            fun valueOf(type: PrismReleaseType): VersionType = when (type) {
                PrismReleaseType.STABLE -> RELEASE
                PrismReleaseType.BETA -> BETA
                PrismReleaseType.ALPHA -> ALPHA
            }
        }
    }

    @Serializable
    data class CreateVersion(
        val name: String,
        @SerialName("version_number") val versionNumber: String,
        val changelog: String? = null,
        val dependencies: List<Dependency>,
        @SerialName("game_versions") val gameVersions: List<String>,
        @SerialName("version_type") val versionType: VersionType,
        val loaders: List<String>,
        val featured: Boolean,
        val status: String? = null,
        @SerialName("requested_status") val requestedStatus: String? = null,
        @SerialName("project_id") val projectId: String,
        @SerialName("file_parts") val fileParts: List<String>,
        @SerialName("primary_file") val primaryFile: String? = null,
    )

    @Serializable
    data class Dependency(
        @SerialName("version_id") val versionId: String? = null,
        @SerialName("project_id") val projectId: String? = null,
        @SerialName("file_name") val fileName: String? = null,
        @SerialName("dependency_type") val dependencyType: DependencyType,
    )

    @Serializable
    enum class DependencyType {
        @SerialName("required") REQUIRED,
        @SerialName("optional") OPTIONAL,
        @SerialName("incompatible") INCOMPATIBLE,
        @SerialName("embedded") EMBEDDED,
        ;
        companion object {
            fun valueOf(type: PublishingDepType): DependencyType = when (type) {
                PublishingDepType.REQUIRED -> REQUIRED
                PublishingDepType.OPTIONAL -> OPTIONAL
                PublishingDepType.INCOMPATIBLE -> INCOMPATIBLE
                PublishingDepType.EMBEDDED -> EMBEDDED
            }
        }
    }

    @Serializable
    data class ListVersionsResponse(
        @SerialName("version_number") val versionNumber: String,
        val id: String,
    )

    @Serializable
    data class CreateVersionResponse(
        val id: String,
        @SerialName("project_id") val projectId: String,
        @SerialName("author_id") val authorId: String,
    )

    @Serializable
    data class ProjectCheckResponse(val id: String)

    @Serializable
    data class ModifyProject(val body: String)

    @Serializable
    data class ErrorResponse(val error: String, val description: String)

    private val headers: Map<String, String>
        get() = mapOf(
            "Authorization" to accessToken,
            "Content-Type" to "application/json",
        )

    fun listVersions(projectSlug: String): Array<ListVersionsResponse> =
        httpUtils.get("$baseUrl/project/$projectSlug/version", headers)

    fun createVersion(metadata: CreateVersion, files: Map<String, Path>): CreateVersionResponse {
        val metadataJson = Json.encodeToString(metadata)
        val bodyBuilder = MultipartBodyBuilder().addFormDataPart("data", metadataJson)
        for ((name, path) in files) {
            bodyBuilder.addFormDataPart(name, path.name, path, "application/java-archive")
        }
        val multipartHeaders = headers.toMutableMap()
        multipartHeaders["Content-Type"] = bodyBuilder.getContentType()
        return httpUtils.post("$baseUrl/version", bodyBuilder.build(), multipartHeaders)
    }

    fun checkProject(projectSlug: String): ProjectCheckResponse =
        httpUtils.get("$baseUrl/project/$projectSlug/check", headers)

    fun modifyProject(projectSlug: String, modifyProject: ModifyProject) {
        val body = HttpRequest.BodyPublishers.ofString(Json.encodeToString(modifyProject))
        httpUtils.patch<String>("$baseUrl/project/$projectSlug", body, headers)
    }

    private class ModrinthHttpExceptionFactory : HttpUtils.HttpExceptionFactory {
        val json = Json { ignoreUnknownKeys = true }
        override fun createException(response: HttpResponse<String>): HttpUtils.HttpException {
            return try {
                val errorResponse = json.decodeFromString<ErrorResponse>(response.body())
                HttpUtils.HttpException(response, errorResponse.description)
            } catch (e: SerializationException) {
                HttpUtils.HttpException(response, "Unknown error")
            }
        }
    }
}
