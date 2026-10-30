package dev.prism.gradle.internal.accesswidener

import dev.prism.gradle.dsl.VersionConfiguration
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.language.jvm.tasks.ProcessResources
import java.io.File
import java.security.MessageDigest

object AccessWidenerSupport {

    private const val CONVERTER_VERSION = 2

    fun resolveAccessWidener(
        project: Project,
        commonProject: Project?,
        unifiedAwPath: String?,
        modId: String
    ): File? {
        val candidate = findAccessWidenerCandidate(project, commonProject, unifiedAwPath, modId) ?: return null

        val parsed = runCatching { AccessWidenerParser.parse(candidate) }.getOrNull()
        if (parsed == null || (parsed.entries.isEmpty() && parsed.unsupportedEntries.isEmpty())) {
            project.logger.info("Prism: Ignoring empty access widener '${candidate.name}' at ${candidate.path}")
            return null
        }

        return candidate
    }

    fun hasAccessTransformerEntries(file: File): Boolean {
        if (!file.exists()) return false
        return file.useLines { lines ->
            lines.any { line ->
                val stripped = line.substringBefore('#').trim()
                stripped.isNotEmpty()
            }
        }
    }

    private fun findAccessWidenerCandidate(
        project: Project,
        commonProject: Project?,
        unifiedAwPath: String?,
        modId: String,
    ): File? {
        if (unifiedAwPath != null) {
            val rootFile = project.rootProject.file(unifiedAwPath)
            if (rootFile.exists()) return rootFile
        }

        val extensions = listOf("classtweaker", "accesswidener")

        if (commonProject != null) {
            for (ext in extensions) {
                val commonFile = commonProject.file("src/main/resources/$modId.$ext")
                if (commonFile.exists()) return commonFile
            }
        }

        for (ext in extensions) {
            val localFile = project.file("src/main/resources/$modId.$ext")
            if (localFile.exists()) return localFile
        }

        return null
    }

    fun generateAccessTransformer(
        project: Project,
        awFile: File,
        targetName: String,
        versionConfig: VersionConfiguration,
        names: AccessTransformerNames,
    ): File {
        val minecraftVersion = versionConfig.minecraftVersion
        if (versionConfig.fabricConfig?.yarnVersion != null) {
            throw GradleException(
                "Prism: Fabric on $minecraftVersion uses Yarn mappings, so '${awFile.name}' can't be converted into an " +
                    "access transformer for $targetName. Move the access widener into the Fabric project and add " +
                    "META-INF/accesstransformer.cfg to the other projects, or remove yarn()."
            )
        }

        val parsed = AccessWidenerParser.parse(awFile)
        val mojangNamespaces = if (isUnobfuscated(minecraftVersion)) setOf("named", "official") else setOf("named")
        if (parsed.namespace !in mojangNamespaces) {
            project.logger.warn("Prism: Access widener '${awFile.name}' uses namespace '${parsed.namespace}', but Prism reads it as Mojang names. The generated access transformer for $targetName may contain incorrect names.")
        }
        if (parsed.unsupportedEntries.isNotEmpty()) {
            project.logger.warn("Prism: Access widener '${awFile.name}' has ${parsed.unsupportedEntries.size} inject-interface/extend-enum entries. Access transformers can't express them, so $targetName won't get them.")
        }

        val outputFile = File(generatedAccessTransformerDir(project), "${targetName}_accesstransformer.cfg")
        val keyFile = File(outputFile.parentFile, "${outputFile.name}.key")
        val key = "$CONVERTER_VERSION|$minecraftVersion|$names|${sha256(awFile)}"
        if (!outputFile.isFile || !keyFile.isFile || keyFile.readText() != key) {
            val resolved = SrgMappings.resolve(project, minecraftVersion, names, parsed)
            AccessWidenerConverter.writeAccessTransformer(parsed, outputFile, resolved)
            if (resolved.unresolved.isEmpty()) {
                keyFile.writeText(key)
            } else {
                keyFile.delete()
                project.logger.warn("Prism: Couldn't find SRG names for these '${awFile.name}' entries in the Minecraft $minecraftVersion mappings, so $targetName keeps their names as written: ${resolved.unresolved.joinToString(", ")}")
            }
        }
        project.logger.lifecycle("Prism: Converted access widener '${awFile.name}' -> '${outputFile.name}' for $targetName (${parsed.entries.size} entries)")

        project.tasks.withType(ProcessResources::class.java).configureEach { task ->
            task.from(outputFile) { copy ->
                copy.into("META-INF")
                copy.rename { "accesstransformer.cfg" }
            }
        }

        return outputFile
    }

    private fun isUnobfuscated(minecraftVersion: String): Boolean =
        (minecraftVersion.split(".").getOrNull(0)?.toIntOrNull() ?: 1) >= 2

    private fun sha256(file: File): String =
        MessageDigest.getInstance("SHA-256").digest(file.readBytes()).joinToString("") { "%02x".format(it) }

    private fun generatedAccessTransformerDir(project: Project): File {
        val cacheDir = project.gradle.startParameter.projectCacheDir ?: project.rootProject.file(".gradle")
        val projectPath = project.path.trim(':').replace(':', '/').ifEmpty { "root" }
        return File(cacheDir, "prism/at/$projectPath")
    }
}
