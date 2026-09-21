package dev.prism.gradle.internal

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.gradle.api.Project
import java.io.File
import java.net.URI

object MinecraftLibraries {
    private const val VERSION_MANIFEST_URL = "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json"

    private val memoryCache = mutableMapOf<String, JsonObject>()

    fun compileLibraries(project: Project, minecraftVersion: String): List<String> =
        libraries(project, minecraftVersion)
            .filter { rulesAllow(it) && coordinateParts(it).size == 3 }
            .map { it["name"]!!.jsonPrimitive.content }
            .associateBy { it.substringBeforeLast(':') }
            .values
            .toList()

    fun hostNatives(project: Project, minecraftVersion: String): List<String> {
        val nativesClassifier = "natives-${hostOs().replace("osx", "macos")}${hostArchSuffix()}"
        return libraries(project, minecraftVersion).filter(::rulesAllow).mapNotNull { library ->
            val parts = coordinateParts(library)
            val legacyClassifier = library["natives"]?.jsonObject?.get(hostOs())?.jsonPrimitive?.content
                ?.replace("\${arch}", if (hostArchSuffix() == "-x86") "32" else "64")
            when {
                parts.size == 4 && parts[3] == nativesClassifier -> parts.joinToString(":")
                parts.size == 3 && legacyClassifier != null -> "${parts.joinToString(":")}:$legacyClassifier"
                else -> null
            }
        }.distinct()
    }

    private fun coordinateParts(library: JsonObject) = library["name"]!!.jsonPrimitive.content.split(":")

    private fun libraries(project: Project, minecraftVersion: String): List<JsonObject> =
        versionJson(project, minecraftVersion)["libraries"]!!.jsonArray.map { it.jsonObject }

    private fun versionJson(project: Project, minecraftVersion: String): JsonObject {
        memoryCache[minecraftVersion]?.let { return it }

        val cacheFile = File(project.gradle.gradleUserHomeDir, "caches/prism/minecraft/$minecraftVersion.json")
        if (!cacheFile.isFile) {
            check(!project.gradle.startParameter.isOffline) {
                "Cannot download the Minecraft $minecraftVersion version manifest in offline mode. Run once online first."
            }
            val manifest = Json.parseToJsonElement(URI(VERSION_MANIFEST_URL).toURL().readText()).jsonObject
            val url = manifest["versions"]!!.jsonArray.map { it.jsonObject }
                .firstOrNull { it["id"]?.jsonPrimitive?.content == minecraftVersion }
                ?.get("url")?.jsonPrimitive?.content
                ?: throw IllegalStateException("Minecraft version '$minecraftVersion' is not in Mojang's version manifest.")
            cacheFile.parentFile.mkdirs()
            cacheFile.writeText(URI(url).toURL().readText())
        }

        return Json.parseToJsonElement(cacheFile.readText()).jsonObject.also { memoryCache[minecraftVersion] = it }
    }

    private fun rulesAllow(library: JsonObject): Boolean {
        val rules = library["rules"] as? JsonArray ?: return true
        var allowed = false
        for (rule in rules.map { it.jsonObject }) {
            val os = rule["os"]?.jsonObject
            val nameMatches = os?.get("name")?.jsonPrimitive?.contentOrNull?.let { it == hostOs() } ?: true
            val archMatches = os?.get("arch")?.jsonPrimitive?.contentOrNull?.let { it == "x86" && hostArchSuffix() == "-x86" } ?: true
            if (nameMatches && archMatches) {
                allowed = rule["action"]?.jsonPrimitive?.content == "allow"
            }
        }
        return allowed
    }

    private fun hostOs(): String {
        val name = System.getProperty("os.name").lowercase()
        return when {
            name.contains("win") -> "windows"
            name.contains("mac") -> "osx"
            else -> "linux"
        }
    }

    private fun hostArchSuffix(): String = when (System.getProperty("os.arch")) {
        "aarch64", "arm64" -> "-arm64"
        "x86", "i386", "i686" -> "-x86"
        else -> ""
    }
}
