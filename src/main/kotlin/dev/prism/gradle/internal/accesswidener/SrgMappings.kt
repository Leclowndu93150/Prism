package dev.prism.gradle.internal.accesswidener

import dev.prism.gradle.internal.MinecraftLibraries
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.gradle.api.Project
import java.io.File
import java.io.OutputStream
import java.net.URI
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.zip.ZipFile

sealed class AccessTransformerNames {
    data object Mojang : AccessTransformerNames()
    data object SrgFromMojang : AccessTransformerNames()
    data class SrgFromMcp(val channel: String, val version: String) : AccessTransformerNames()
}

class ResolvedNames(
    private val classes: Map<String, String>,
    private val members: Map<String, String>,
    val unresolved: List<String>,
) : AccessTransformerRemapper {
    override fun mapClass(name: String): String = classes[name] ?: name
    override fun mapField(owner: String, name: String): String = members["$owner.$name"] ?: name
    override fun mapMethod(owner: String, name: String, descriptor: String): String = members["$owner.$name$descriptor"] ?: name
    override fun mapDescriptor(descriptor: String): String = SrgMappings.remapDescriptor(descriptor, classes)
}

object SrgMappings {
    private const val FORGE_MAVEN = "https://maven.minecraftforge.net/de/oceanlabs/mcp"
    private val CLASS_IN_DESCRIPTOR = Regex("L([^;]+);")
    private val LINE_NUMBERS = Regex("^\\d+:\\d+:")
    private val TRAILING_LINE_NUMBERS = Regex(":\\d+(:\\d+)?$")
    private val PRIMITIVES = mapOf(
        "boolean" to "Z", "byte" to "B", "char" to "C", "short" to "S",
        "int" to "I", "long" to "J", "float" to "F", "double" to "D", "void" to "V"
    )

    private class MemberTable {
        val fields = mutableMapOf<String, String>()
        val methods = mutableMapOf<String, String>()
    }

    private class Proguard(val classes: Map<String, String>, val members: Map<String, MemberTable>)

    private class SrgField(val obf: String, val srg: String)
    private class SrgMethod(val obf: String, val obfDescriptor: String, val srg: String)

    private class SrgClass(val obf: String, val srg: String) {
        val fields = mutableListOf<SrgField>()
        val methods = mutableListOf<SrgMethod>()
    }

    private class Tsrg(val classesByObf: Map<String, SrgClass>)

    fun resolve(project: Project, minecraftVersion: String, names: AccessTransformerNames, aw: AccessWidenerFile): ResolvedNames =
        when (names) {
            AccessTransformerNames.Mojang -> ResolvedNames(emptyMap(), emptyMap(), emptyList())
            AccessTransformerNames.SrgFromMojang -> fromMojang(
                aw,
                clientMappings(project, minecraftVersion),
                joinedTsrg(project, minecraftVersion),
                renameClasses = !usesMojangClassNamesAtRuntime(minecraftVersion)
            )
            is AccessTransformerNames.SrgFromMcp -> fromMcp(
                aw,
                joinedTsrg(project, minecraftVersion),
                mcpNames(project, names.channel, names.version)
            )
        }

    fun remapDescriptor(descriptor: String, classes: Map<String, String>): String =
        CLASS_IN_DESCRIPTOR.replace(descriptor) { match -> "L${classes[match.groupValues[1]] ?: match.groupValues[1]};" }

    private fun fromMojang(aw: AccessWidenerFile, clientMappings: File, tsrgFile: File, renameClasses: Boolean): ResolvedNames {
        val owners = aw.entries.map { it.className }.toSet()
        val proguard = readProguard(clientMappings, owners)
        val tsrg = readTsrg(tsrgFile, owners.mapNotNull { proguard.classes[it] }.toSet())

        val referencedClasses = owners + aw.entries.flatMap { entry ->
            val descriptor = (entry as? AccessWidenerEntry.MethodEntry)?.descriptor ?: ""
            CLASS_IN_DESCRIPTOR.findAll(descriptor).map { it.groupValues[1] }.toList()
        }
        val toObf = proguard.classes.filterKeys { it in referencedClasses }
        val classes = if (renameClasses) {
            toObf.mapNotNull { (mojang, obf) -> tsrg.classesByObf[obf]?.let { mojang to it.srg } }.toMap()
        } else {
            emptyMap()
        }

        val members = mutableMapOf<String, String>()
        val unresolved = mutableListOf<String>()
        for (entry in aw.entries) {
            val obfOwner = proguard.classes[entry.className] ?: continue
            val table = proguard.members[entry.className] ?: MemberTable()
            val srgClass = tsrg.classesByObf[obfOwner]
            when (entry) {
                is AccessWidenerEntry.ClassEntry -> Unit
                is AccessWidenerEntry.FieldEntry -> {
                    val obf = table.fields[entry.fieldName]
                    if (obf == null) {
                        unresolved.add("${entry.className}.${entry.fieldName}")
                    } else {
                        members["${entry.className}.${entry.fieldName}"] = srgClass?.fields?.firstOrNull { it.obf == obf }?.srg ?: obf
                    }
                }
                is AccessWidenerEntry.MethodEntry -> {
                    if (entry.methodName.startsWith("<")) continue
                    val key = entry.methodName + entry.descriptor
                    val obf = table.methods[key]
                    if (obf == null) {
                        unresolved.add("${entry.className}.$key")
                    } else {
                        val obfDescriptor = remapDescriptor(entry.descriptor, toObf)
                        members["${entry.className}.$key"] = srgClass?.methods
                            ?.firstOrNull { it.obf == obf && it.obfDescriptor == obfDescriptor }?.srg ?: obf
                    }
                }
            }
        }
        return ResolvedNames(classes, members, unresolved)
    }

    private fun fromMcp(aw: AccessWidenerFile, tsrgFile: File, mcpNames: Map<String, String>): ResolvedNames {
        val tsrg = readTsrg(tsrgFile, null)
        val bySrg = tsrg.classesByObf.values.associateBy { it.srg }
        val obfToSrg = tsrg.classesByObf.mapValues { it.value.srg }

        val members = mutableMapOf<String, String>()
        val unresolved = mutableListOf<String>()
        for (entry in aw.entries) {
            val srgClass = bySrg[entry.className] ?: continue
            when (entry) {
                is AccessWidenerEntry.ClassEntry -> Unit
                is AccessWidenerEntry.FieldEntry -> {
                    val match = srgClass.fields.filter { it.srg == entry.fieldName || mcpNames[it.srg] == entry.fieldName }
                    if (match.size == 1) {
                        members["${entry.className}.${entry.fieldName}"] = match.single().srg
                    } else {
                        unresolved.add("${entry.className}.${entry.fieldName}")
                    }
                }
                is AccessWidenerEntry.MethodEntry -> {
                    if (entry.methodName.startsWith("<")) continue
                    val key = entry.methodName + entry.descriptor
                    val match = srgClass.methods.filter {
                        (it.srg == entry.methodName || mcpNames[it.srg] == entry.methodName) &&
                            remapDescriptor(it.obfDescriptor, obfToSrg) == entry.descriptor
                    }
                    if (match.size == 1) {
                        members["${entry.className}.$key"] = match.single().srg
                    } else {
                        unresolved.add("${entry.className}.$key")
                    }
                }
            }
        }
        return ResolvedNames(emptyMap(), members, unresolved)
    }

    private fun usesMojangClassNamesAtRuntime(minecraftVersion: String): Boolean {
        val parts = minecraftVersion.split(".").map { it.toIntOrNull() ?: 0 }
        val major = parts.getOrElse(0) { 1 }
        val minor = parts.getOrElse(1) { 0 }
        return major > 1 || minor >= 17
    }

    private fun readProguard(file: File, owners: Set<String>): Proguard {
        val classes = mutableMapOf<String, String>()
        val members = mutableMapOf<String, MemberTable>()
        var current: MemberTable? = null
        file.forEachLine { line ->
            if (line.isEmpty() || line.trimStart().startsWith("#")) return@forEachLine
            if (!line[0].isWhitespace()) {
                val mojang = line.substringBefore(" -> ").replace('.', '/')
                classes[mojang] = line.substringAfter(" -> ").removeSuffix(":").replace('.', '/')
                current = if (mojang in owners) MemberTable().also { members[mojang] = it } else null
                return@forEachLine
            }
            val table = current ?: return@forEachLine
            val signature = line.trim().substringBefore(" -> ")
            val obf = line.substringAfter(" -> ").trim()
            if (signature.contains('(')) {
                val cleaned = signature.replace(LINE_NUMBERS, "").replace(TRAILING_LINE_NUMBERS, "")
                val returnType = cleaned.substringBefore(' ')
                val name = cleaned.substringAfter(' ').substringBefore('(')
                val arguments = cleaned.substringAfter('(').substringBefore(')')
                    .split(',').filter { it.isNotBlank() }
                val descriptor = arguments.joinToString("", "(", ")") { javaTypeToDescriptor(it) } + javaTypeToDescriptor(returnType)
                table.methods.putIfAbsent(name + descriptor, obf)
            } else {
                table.fields.putIfAbsent(signature.substringAfter(' '), obf)
            }
        }
        return Proguard(classes, members)
    }

    private fun javaTypeToDescriptor(type: String): String {
        val dimensions = type.count { it == '[' }
        val base = type.substringBefore('[').trim()
        val element = PRIMITIVES[base] ?: "L${base.replace('.', '/')};"
        return "[".repeat(dimensions) + element
    }

    private fun readTsrg(file: File, obfOwners: Set<String>?): Tsrg {
        val lines = file.readLines()
        val header = lines.firstOrNull()?.takeIf { it.startsWith("tsrg2 ") }
        val namespaces = header?.split(' ')?.drop(1) ?: listOf("obf", "srg")
        val obfIndex = namespaces.indexOf("obf")
        val srgIndex = namespaces.indexOf("srg")
        require(obfIndex >= 0 && srgIndex >= 0) { "Unsupported TSRG namespaces in ${file.name}: $namespaces" }

        val classes = mutableMapOf<String, SrgClass>()
        var current: SrgClass? = null
        for (line in if (header != null) lines.drop(1) else lines) {
            if (line.isBlank() || line.startsWith("\t\t")) continue
            val tokens = line.trim().split(' ', '\t').filter { it.isNotEmpty() }
            if (!line.startsWith("\t")) {
                val srgClass = SrgClass(tokens[obfIndex], tokens[srgIndex])
                classes[srgClass.obf] = srgClass
                current = srgClass.takeIf { obfOwners == null || it.obf in obfOwners }
                continue
            }
            val owner = current ?: continue
            if (tokens.size > 1 && tokens[1].startsWith("(")) {
                val memberNames = listOf(tokens[0]) + tokens.drop(2)
                owner.methods.add(SrgMethod(memberNames[obfIndex], tokens[1], memberNames[srgIndex]))
            } else {
                owner.fields.add(SrgField(tokens[obfIndex], tokens[srgIndex]))
            }
        }
        return Tsrg(classes)
    }

    private fun clientMappings(project: Project, minecraftVersion: String): File =
        cachedDownload(
            project,
            "minecraft/$minecraftVersion-client-mappings.txt"
        ) { MinecraftLibraries.clientMappingsUrl(project, minecraftVersion) }

    private fun joinedTsrg(project: Project, minecraftVersion: String): File {
        val zip = cachedDownload(
            project,
            "mcp/mcp_config-$minecraftVersion.zip"
        ) { "$FORGE_MAVEN/mcp_config/$minecraftVersion/mcp_config-$minecraftVersion.zip" }
        val output = File(zip.parentFile, "mcp_config-$minecraftVersion-joined.tsrg")
        if (!output.isFile) {
            ZipFile(zip).use { archive ->
                val config = archive.getEntry("config.json")?.let { entry ->
                    Json.parseToJsonElement(archive.getInputStream(entry).bufferedReader().readText()).jsonObject
                }
                val path = config?.get("data")?.jsonObject?.get("mappings")?.jsonPrimitive?.contentOrNull ?: "config/joined.tsrg"
                val entry = archive.getEntry(path) ?: error("MCPConfig $minecraftVersion has no $path")
                writeAtomically(output) { archive.getInputStream(entry).use { input -> input.copyTo(it) } }
            }
        }
        return output
    }

    private fun mcpNames(project: Project, channel: String, version: String): Map<String, String> {
        val zip = cachedDownload(project, "mcp/mcp_$channel-$version.zip") { "$FORGE_MAVEN/mcp_$channel/$version/mcp_$channel-$version.zip" }
        val names = mutableMapOf<String, String>()
        ZipFile(zip).use { archive ->
            for (csv in listOf("fields.csv", "methods.csv")) {
                val entry = archive.getEntry(csv) ?: continue
                archive.getInputStream(entry).bufferedReader().lineSequence().drop(1).forEach { line ->
                    val columns = line.split(',', limit = 3)
                    if (columns.size >= 2) names[columns[0]] = columns[1]
                }
            }
        }
        return names
    }

    private fun cachedDownload(project: Project, relativePath: String, url: () -> String): File {
        val file = File(project.gradle.gradleUserHomeDir, "caches/prism/$relativePath")
        if (!file.isFile) {
            check(!project.gradle.startParameter.isOffline) {
                "Cannot download ${file.name} in offline mode. Run once online first."
            }
            writeAtomically(file) { output -> URI(url()).toURL().openStream().use { it.copyTo(output) } }
        }
        return file
    }

    private fun writeAtomically(file: File, write: (OutputStream) -> Unit) {
        file.parentFile.mkdirs()
        val temp = File.createTempFile(file.name, ".part", file.parentFile)
        temp.outputStream().use(write)
        Files.move(temp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
    }
}
