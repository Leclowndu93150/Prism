package dev.prism.gradle.internal.accesswidener

import java.io.File

interface AccessTransformerRemapper {
    fun mapClass(name: String): String = name
    fun mapField(owner: String, name: String): String = name
    fun mapMethod(owner: String, name: String, descriptor: String): String = name
    fun mapDescriptor(descriptor: String): String = descriptor

    object Identity : AccessTransformerRemapper
}

object AccessWidenerConverter {

    private enum class Visibility(val keyword: String) {
        PUBLIC("public"),
        PROTECTED("protected"),
        PRIVATE("private")
    }

    private data class AtAccess(val visibility: Visibility, val removeFinal: Boolean) {
        fun merge(other: AtAccess) = AtAccess(minOf(visibility, other.visibility), removeFinal || other.removeFinal)

        override fun toString() = if (removeFinal) "${visibility.keyword}-f" else visibility.keyword
    }

    private sealed class AtTarget {
        data class Class(val name: String) : AtTarget()
        data class Method(val owner: String, val name: String, val descriptor: String) : AtTarget()
        data class Field(val owner: String, val name: String) : AtTarget()
    }

    private val PUBLIC = AtAccess(Visibility.PUBLIC, false)
    private val PUBLIC_NON_FINAL = AtAccess(Visibility.PUBLIC, true)
    private val PROTECTED_NON_FINAL = AtAccess(Visibility.PROTECTED, true)
    private val NON_FINAL = AtAccess(Visibility.PRIVATE, true)

    fun convertToAccessTransformer(
        aw: AccessWidenerFile,
        remapper: AccessTransformerRemapper = AccessTransformerRemapper.Identity
    ): String {
        val targets = linkedMapOf<AtTarget, AtAccess>()
        val ownerClasses = linkedMapOf<AtTarget, AtAccess>()

        for (entry in aw.entries) {
            val access = entryAccess(entry) ?: continue
            targets.merge(target(entry), access, AtAccess::merge)
            ownerAccess(entry)?.let { ownerClasses.merge(AtTarget.Class(entry.className), it, AtAccess::merge) }
        }
        for ((target, access) in ownerClasses) {
            targets.merge(target, access, AtAccess::merge)
        }

        return targets.entries.joinToString("\n") { (target, access) -> "$access ${render(target, remapper)}" }
    }

    fun writeAccessTransformer(
        aw: AccessWidenerFile,
        outputFile: File,
        remapper: AccessTransformerRemapper = AccessTransformerRemapper.Identity
    ) {
        outputFile.parentFile.mkdirs()
        outputFile.writeText(convertToAccessTransformer(aw, remapper))
    }

    private fun target(entry: AccessWidenerEntry): AtTarget = when (entry) {
        is AccessWidenerEntry.ClassEntry -> AtTarget.Class(entry.className)
        is AccessWidenerEntry.MethodEntry -> AtTarget.Method(entry.className, entry.methodName, entry.descriptor)
        is AccessWidenerEntry.FieldEntry -> AtTarget.Field(entry.className, entry.fieldName)
    }

    private fun entryAccess(entry: AccessWidenerEntry): AtAccess? = when (entry) {
        is AccessWidenerEntry.ClassEntry -> when (entry.modifier) {
            AccessModifier.ACCESSIBLE -> PUBLIC
            AccessModifier.EXTENDABLE -> PUBLIC_NON_FINAL
            AccessModifier.MUTABLE -> null
        }
        is AccessWidenerEntry.MethodEntry -> when (entry.modifier) {
            AccessModifier.ACCESSIBLE -> PUBLIC
            AccessModifier.EXTENDABLE -> PROTECTED_NON_FINAL
            AccessModifier.MUTABLE -> null
        }
        is AccessWidenerEntry.FieldEntry -> when (entry.modifier) {
            AccessModifier.ACCESSIBLE -> PUBLIC
            AccessModifier.MUTABLE -> NON_FINAL
            AccessModifier.EXTENDABLE -> null
        }
    }

    private fun ownerAccess(entry: AccessWidenerEntry): AtAccess? = when (entry) {
        is AccessWidenerEntry.ClassEntry -> null
        is AccessWidenerEntry.MethodEntry -> when (entry.modifier) {
            AccessModifier.ACCESSIBLE -> PUBLIC
            AccessModifier.EXTENDABLE -> PUBLIC_NON_FINAL
            AccessModifier.MUTABLE -> null
        }
        is AccessWidenerEntry.FieldEntry -> if (entry.modifier == AccessModifier.ACCESSIBLE) PUBLIC else null
    }

    private fun render(target: AtTarget, remapper: AccessTransformerRemapper): String = when (target) {
        is AtTarget.Class -> dotName(remapper.mapClass(target.name))
        is AtTarget.Method -> "${dotName(remapper.mapClass(target.owner))} " +
            remapper.mapMethod(target.owner, target.name, target.descriptor) +
            remapper.mapDescriptor(target.descriptor)
        is AtTarget.Field -> "${dotName(remapper.mapClass(target.owner))} ${remapper.mapField(target.owner, target.name)}"
    }

    private fun dotName(internalName: String): String = internalName.replace('/', '.')
}
