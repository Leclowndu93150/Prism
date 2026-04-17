// MIT License
// Copyright (c) 2023 modmuss50 - https://github.com/modmuss50/mod-publish-plugin
// Vendored into Prism.
package dev.prism.gradle.internal.publish.curseforge

import org.gradle.api.JavaVersion

class CurseforgeVersions(
    private val versionTypes: List<CurseforgeApi.GameVersionType>,
    private val versions: List<CurseforgeApi.GameVersion>,
) {
    private fun getGameVersionTypes(name: String): List<Int> {
        val result = if (name == "minecraft") {
            versionTypes.filter { it.slug.startsWith("minecraft") }
        } else {
            versionTypes.filter { it.slug == name }
        }.map { it.id }
        if (result.isEmpty()) {
            throw IllegalStateException("Failed to find version type: $name")
        }
        return result
    }

    private fun getVersion(name: String, type: String): Int {
        val types = getGameVersionTypes(type)
        val version = versions.find { types.contains(it.gameVersionTypeID) && it.name.equals(name, ignoreCase = true) }
            ?: throw IllegalStateException("Failed to find version: $name")
        return version.id
    }

    fun getMinecraftVersion(name: String): Int = getVersion(name, "minecraft")
    fun getModLoaderVersion(name: String): Int = getVersion(name, "modloader")
    fun getClientVersion(): Int = getVersion("client", "environment")
    fun getServerVersion(): Int = getVersion("server", "environment")
    fun getJavaVersion(version: JavaVersion): Int = getVersion("Java ${version.ordinal + 1}", "java")
}
