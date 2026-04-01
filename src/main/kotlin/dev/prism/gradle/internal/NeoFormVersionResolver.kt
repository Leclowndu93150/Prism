package dev.prism.gradle.internal

import org.gradle.api.Project
import java.io.File
import java.net.URI
import javax.xml.parsers.DocumentBuilderFactory

object NeoFormVersionResolver {
    private const val NEOFORM_METADATA_URL =
        "https://maven.neoforged.net/releases/net/neoforged/neoform/maven-metadata.xml"

    private val memoryCache = mutableMapOf<String, String>()

    fun resolveNeoForm(minecraftVersion: String, project: Project): String {
        memoryCache[minecraftVersion]?.let { return it }

        val cacheFile = File(project.gradle.gradleUserHomeDir, "caches/prism/neoform-versions.txt")
        val cached = readFromDiskCache(cacheFile, minecraftVersion)
        if (cached != null) {
            memoryCache[minecraftVersion] = cached
            return cached
        }

        if (project.gradle.startParameter.isOffline) {
            throw IllegalStateException(
                "Cannot resolve NeoForm version for Minecraft $minecraftVersion in offline mode. " +
                "Set neoFormVersion manually in the version block, or run once online first."
            )
        }

        val versions = fetchAvailableVersions()
        val matching = versions.filter { it.startsWith("$minecraftVersion-") || it == minecraftVersion }

        val resolved = matching.maxByOrNull { extractTimestamp(it) }
            ?: throw IllegalStateException(
                "Could not find NeoForm version for Minecraft $minecraftVersion. " +
                "Set neoFormVersion manually in the version block."
            )

        memoryCache[minecraftVersion] = resolved
        writeToDiskCache(cacheFile, minecraftVersion, resolved)
        return resolved
    }

    private fun fetchAvailableVersions(): List<String> {
        val factory = DocumentBuilderFactory.newInstance()
        val builder = factory.newDocumentBuilder()
        val doc = URI(NEOFORM_METADATA_URL).toURL().openStream().use { builder.parse(it) }

        val versionNodes = doc.getElementsByTagName("version")
        val versions = mutableListOf<String>()
        for (i in 0 until versionNodes.length) {
            versions.add(versionNodes.item(i).textContent.trim())
        }
        return versions
    }

    private fun extractTimestamp(version: String): Long {
        val dash = version.indexOf('-')
        if (dash < 0) return 0
        return version.substring(dash + 1).replace(".", "").toLongOrNull() ?: 0
    }

    private fun readFromDiskCache(cacheFile: File, minecraftVersion: String): String? {
        if (!cacheFile.exists()) return null
        val maxAge = 24 * 60 * 60 * 1000L
        if (System.currentTimeMillis() - cacheFile.lastModified() > maxAge) return null

        return cacheFile.readLines()
            .map { it.split("=", limit = 2) }
            .filter { it.size == 2 }
            .firstOrNull { it[0] == minecraftVersion }
            ?.get(1)
    }

    private fun writeToDiskCache(cacheFile: File, minecraftVersion: String, resolved: String) {
        cacheFile.parentFile.mkdirs()
        val existing = if (cacheFile.exists()) {
            cacheFile.readLines()
                .filter { !it.startsWith("$minecraftVersion=") }
        } else {
            emptyList()
        }
        cacheFile.writeText((existing + "$minecraftVersion=$resolved").joinToString("\n"))
    }
}
