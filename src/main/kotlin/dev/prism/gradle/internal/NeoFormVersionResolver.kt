package dev.prism.gradle.internal

import org.gradle.api.Project
import java.io.File
import java.net.URI
import javax.xml.parsers.DocumentBuilderFactory

data class CommonMinecraftDep(val artifact: String, val version: String, val useMcp: Boolean)

object NeoFormVersionResolver {
    private const val NEOFORM_METADATA_URL =
        "https://maven.neoforged.net/releases/net/neoforged/neoform/maven-metadata.xml"
    private const val MCP_METADATA_URL =
        "https://maven.neoforged.net/releases/de/oceanlabs/mcp/mcp_config/maven-metadata.xml"

    private val memoryCache = mutableMapOf<String, CommonMinecraftDep>()

    fun resolve(minecraftVersion: String, project: Project): CommonMinecraftDep {
        memoryCache[minecraftVersion]?.let { return it }

        val cacheFile = File(project.gradle.gradleUserHomeDir, "caches/prism/neoform-versions.txt")
        val cached = readFromDiskCache(cacheFile, minecraftVersion)
        if (cached != null) {
            memoryCache[minecraftVersion] = cached
            return cached
        }

        if (project.gradle.startParameter.isOffline) {
            throw IllegalStateException(
                "Cannot resolve NeoForm/MCP version for Minecraft $minecraftVersion in offline mode. " +
                "Set neoFormVersion manually in the version block, or run once online first."
            )
        }

        val neoFormVersion = tryResolveFromMaven(NEOFORM_METADATA_URL, minecraftVersion)
        if (neoFormVersion != null) {
            val dep = CommonMinecraftDep("neoform", neoFormVersion, false)
            memoryCache[minecraftVersion] = dep
            writeToDiskCache(cacheFile, minecraftVersion, "neoform:$neoFormVersion")
            return dep
        }

        val mcpVersion = tryResolveFromMaven(MCP_METADATA_URL, minecraftVersion)
        if (mcpVersion != null) {
            val dep = CommonMinecraftDep("mcp", mcpVersion, true)
            memoryCache[minecraftVersion] = dep
            writeToDiskCache(cacheFile, minecraftVersion, "mcp:$mcpVersion")
            return dep
        }

        throw IllegalStateException(
            "Could not find NeoForm or MCP version for Minecraft $minecraftVersion. " +
            "Set neoFormVersion manually in the version block."
        )
    }

    private fun tryResolveFromMaven(metadataUrl: String, minecraftVersion: String): String? {
        val versions = fetchAvailableVersions(metadataUrl)
        val matching = versions.filter { it.startsWith("$minecraftVersion-") || it == minecraftVersion }
        return matching.maxByOrNull { extractTimestamp(it) }
    }

    private fun fetchAvailableVersions(url: String): List<String> {
        val factory = DocumentBuilderFactory.newInstance()
        val builder = factory.newDocumentBuilder()
        val doc = URI(url).toURL().openStream().use { builder.parse(it) }

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

    private fun readFromDiskCache(cacheFile: File, minecraftVersion: String): CommonMinecraftDep? {
        if (!cacheFile.exists()) return null
        val maxAge = 24 * 60 * 60 * 1000L
        if (System.currentTimeMillis() - cacheFile.lastModified() > maxAge) return null

        val line = cacheFile.readLines()
            .map { it.split("=", limit = 2) }
            .filter { it.size == 2 }
            .firstOrNull { it[0] == minecraftVersion }
            ?.get(1) ?: return null

        val parts = line.split(":", limit = 2)
        if (parts.size != 2) return null
        return CommonMinecraftDep(parts[0], parts[1], parts[0] == "mcp")
    }

    private fun writeToDiskCache(cacheFile: File, minecraftVersion: String, value: String) {
        cacheFile.parentFile.mkdirs()
        val existing = if (cacheFile.exists()) {
            cacheFile.readLines()
                .filter { !it.startsWith("$minecraftVersion=") }
        } else {
            emptyList()
        }
        cacheFile.writeText((existing + "$minecraftVersion=$value").joinToString("\n"))
    }
}
