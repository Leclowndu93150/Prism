// MIT License
// Copyright (c) 2023 modmuss50 - https://github.com/modmuss50/mod-publish-plugin
// Vendored into Prism, PlatformDependency refs swapped for Prism's PublishingDepType.
package dev.prism.gradle.internal.publish.curseforge

import dev.prism.gradle.dsl.PublishingDepType
import dev.prism.gradle.dsl.ReleaseType as PrismReleaseType
import dev.prism.gradle.internal.publish.HttpUtils
import dev.prism.gradle.internal.publish.MultipartBodyBuilder
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.net.http.HttpResponse
import java.nio.file.Path
import kotlin.io.path.name

// https://support.curseforge.com/en/support/solutions/articles/9000197321-curseforge-upload-api
class CurseforgeApi(private val accessToken: String, private val baseUrl: String) {
    @OptIn(ExperimentalSerializationApi::class)
    val json = Json { explicitNulls = false }

    private val httpUtils = HttpUtils(exceptionFactory = CurseforgeHttpExceptionFactory())

    @Serializable
    data class GameVersionType(val id: Int, val name: String, val slug: String)

    @Serializable
    data class GameVersion(val id: Int, val gameVersionTypeID: Int, val name: String, val slug: String)

    @Serializable
    enum class ReleaseType {
        @SerialName("alpha") ALPHA,
        @SerialName("beta") BETA,
        @SerialName("release") RELEASE,
        ;
        companion object {
            fun valueOf(type: PrismReleaseType): ReleaseType = when (type) {
                PrismReleaseType.STABLE -> RELEASE
                PrismReleaseType.BETA -> BETA
                PrismReleaseType.ALPHA -> ALPHA
            }
        }
    }

    @Serializable
    enum class ChangelogType {
        @SerialName("text") TEXT,
        @SerialName("html") HTML,
        @SerialName("markdown") MARKDOWN,
    }

    @Serializable
    data class UploadFileMetadata(
        val changelog: String,
        val changelogType: ChangelogType? = null,
        val displayName: String? = null,
        val parentFileID: Int? = null,
        val gameVersions: List<Int>?,
        val releaseType: ReleaseType,
        val relations: UploadFileRelations? = null,
    )

    @Serializable
    data class UploadFileRelations(val projects: List<ProjectFileRelation>)

    @Serializable
    enum class RelationType {
        @SerialName("embeddedLibrary") EMBEDDED_LIBRARY,
        @SerialName("incompatible") INCOMPATIBLE,
        @SerialName("optionalDependency") OPTIONAL_DEPENDENCY,
        @SerialName("requiredDependency") REQUIRED_DEPENDENCY,
        @SerialName("tool") TOOL,
        ;
        companion object {
            fun valueOf(type: PublishingDepType): RelationType = when (type) {
                PublishingDepType.REQUIRED -> REQUIRED_DEPENDENCY
                PublishingDepType.OPTIONAL -> OPTIONAL_DEPENDENCY
                PublishingDepType.INCOMPATIBLE -> INCOMPATIBLE
                PublishingDepType.EMBEDDED -> EMBEDDED_LIBRARY
            }
        }
    }

    @Serializable
    data class ProjectFileRelation(val slug: String, val type: RelationType)

    @Serializable
    data class UploadFileResponse(val id: Int)

    @Serializable
    data class ErrorResponse(val errorCode: Int, val errorMessage: String)

    private val headers: Map<String, String>
        get() = mapOf("X-Api-Token" to accessToken)

    fun getVersionTypes(): List<GameVersionType> =
        httpUtils.get("$baseUrl/api/game/version-types", headers)

    fun getGameVersions(): List<GameVersion> =
        httpUtils.get("$baseUrl/api/game/versions", headers)

    fun uploadFile(projectId: String, path: Path, uploadMetadata: UploadFileMetadata): UploadFileResponse {
        val metadataJson = json.encodeToString(uploadMetadata)
        val bodyBuilder = MultipartBodyBuilder()
            .addFormDataPart("file", path.name, path, "application/java-archive")
            .addFormDataPart("metadata", metadataJson)
        val multipartHeaders = headers.toMutableMap()
        multipartHeaders["Content-Type"] = bodyBuilder.getContentType()
        return httpUtils.post("$baseUrl/api/projects/$projectId/upload-file", bodyBuilder.build(), multipartHeaders)
    }

    private class CurseforgeHttpExceptionFactory : HttpUtils.HttpExceptionFactory {
        val json = Json { ignoreUnknownKeys = true }
        override fun createException(response: HttpResponse<String>): HttpUtils.HttpException {
            return try {
                val errorResponse = json.decodeFromString<ErrorResponse>(response.body())
                HttpUtils.HttpException(response, errorResponse.errorMessage)
            } catch (e: SerializationException) {
                HttpUtils.HttpException(response, "Unknown error")
            }
        }
    }
}
