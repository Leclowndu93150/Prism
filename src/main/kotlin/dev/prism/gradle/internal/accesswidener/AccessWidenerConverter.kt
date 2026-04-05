package dev.prism.gradle.internal.accesswidener

import java.io.File

object AccessWidenerConverter {

    fun convertToAccessTransformer(aw: AccessWidenerFile): String {
        val lines = mutableListOf<String>()
        for (entry in aw.entries) {
            when (entry) {
                is AccessWidenerEntry.ClassEntry -> {
                    lines.add("${atModifier(entry.modifier)} ${dotName(entry.className)}")
                }
                is AccessWidenerEntry.MethodEntry -> {
                    lines.add("${atModifier(entry.modifier)} ${dotName(entry.className)} ${entry.methodName}${entry.descriptor}")
                }
                is AccessWidenerEntry.FieldEntry -> {
                    lines.add("${atModifier(entry.modifier)} ${dotName(entry.className)} ${entry.fieldName}")
                }
            }
        }
        return lines.joinToString("\n")
    }

    fun writeAccessTransformer(aw: AccessWidenerFile, outputFile: File) {
        outputFile.parentFile.mkdirs()
        outputFile.writeText(convertToAccessTransformer(aw))
    }

    private fun atModifier(modifier: AccessModifier): String = when (modifier) {
        AccessModifier.ACCESSIBLE -> "public"
        AccessModifier.MUTABLE -> "public-f"
        AccessModifier.EXTENDABLE -> "protected-f"
    }

    private fun dotName(internalName: String): String = internalName.replace('/', '.')
}
