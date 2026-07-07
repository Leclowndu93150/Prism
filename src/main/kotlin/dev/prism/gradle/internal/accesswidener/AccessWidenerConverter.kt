package dev.prism.gradle.internal.accesswidener

import java.io.File

object AccessWidenerConverter {

    fun convertToAccessTransformer(aw: AccessWidenerFile): String {
        val lines = mutableListOf<String>()
        // Fabric access wideners implicitly make the owning class accessible whenever a member
        // is made accessible. Access transformers do not, so emit an explicit class line for each
        // class that owns an accessible member (deduplicated, only for ACCESSIBLE entries).
        val classesToOpen = linkedSetOf<String>()
        for (entry in aw.entries) {
            when (entry) {
                is AccessWidenerEntry.ClassEntry -> {
                    lines.add("${atModifier(entry.modifier)} ${dotName(entry.className)}")
                }
                is AccessWidenerEntry.MethodEntry -> {
                    lines.add("${atModifier(entry.modifier)} ${dotName(entry.className)} ${entry.methodName}${entry.descriptor}")
                    if (entry.modifier == AccessModifier.ACCESSIBLE) classesToOpen.add(entry.className)
                }
                is AccessWidenerEntry.FieldEntry -> {
                    lines.add("${atModifier(entry.modifier)} ${dotName(entry.className)} ${entry.fieldName}")
                    if (entry.modifier == AccessModifier.ACCESSIBLE) classesToOpen.add(entry.className)
                }
            }
        }
        for (className in classesToOpen) {
            lines.add("public ${dotName(className)}")
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
