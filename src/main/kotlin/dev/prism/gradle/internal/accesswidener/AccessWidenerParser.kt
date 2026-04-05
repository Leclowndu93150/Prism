package dev.prism.gradle.internal.accesswidener

import java.io.File

enum class AccessModifier {
    ACCESSIBLE,
    MUTABLE,
    EXTENDABLE
}

sealed class AccessWidenerEntry {
    abstract val modifier: AccessModifier

    data class ClassEntry(
        override val modifier: AccessModifier,
        val className: String
    ) : AccessWidenerEntry()

    data class MethodEntry(
        override val modifier: AccessModifier,
        val className: String,
        val methodName: String,
        val descriptor: String
    ) : AccessWidenerEntry()

    data class FieldEntry(
        override val modifier: AccessModifier,
        val className: String,
        val fieldName: String,
        val descriptor: String
    ) : AccessWidenerEntry()
}

data class AccessWidenerFile(
    val namespace: String,
    val entries: List<AccessWidenerEntry>
)

object AccessWidenerParser {

    fun parse(file: File): AccessWidenerFile {
        val lines = file.readLines()
            .map { it.substringBefore('#').trim() }
            .filter { it.isNotEmpty() }

        require(lines.isNotEmpty()) { "Access widener file is empty: ${file.path}" }

        val header = lines[0]
        val headerParts = header.split("\\s+".toRegex())
        require(headerParts.size >= 2 && headerParts[0] == "accessWidener") {
            "Invalid access widener header: $header"
        }

        val namespace = if (headerParts.size >= 3) headerParts[2] else "named"

        val entries = lines.drop(1).mapNotNull { line ->
            val parts = line.split("\\s+".toRegex())
            if (parts.size < 3) return@mapNotNull null

            val modifier = when (parts[0]) {
                "accessible" -> AccessModifier.ACCESSIBLE
                "mutable" -> AccessModifier.MUTABLE
                "extendable" -> AccessModifier.EXTENDABLE
                else -> return@mapNotNull null
            }

            when (parts[1]) {
                "class" -> {
                    AccessWidenerEntry.ClassEntry(modifier, parts[2])
                }
                "method" -> {
                    if (parts.size < 5) return@mapNotNull null
                    AccessWidenerEntry.MethodEntry(modifier, parts[2], parts[3], parts[4])
                }
                "field" -> {
                    if (parts.size < 5) return@mapNotNull null
                    AccessWidenerEntry.FieldEntry(modifier, parts[2], parts[3], parts[4])
                }
                else -> null
            }
        }

        return AccessWidenerFile(namespace, entries)
    }
}
