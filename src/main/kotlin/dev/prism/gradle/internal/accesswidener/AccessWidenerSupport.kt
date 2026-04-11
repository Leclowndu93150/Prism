package dev.prism.gradle.internal.accesswidener

import org.gradle.api.Project
import org.gradle.language.jvm.tasks.ProcessResources
import java.io.File

object AccessWidenerSupport {

    fun resolveAccessWidener(
        project: Project,
        commonProject: Project?,
        unifiedAwPath: String?,
        modId: String
    ): File? {
        val candidate = findAccessWidenerCandidate(project, commonProject, unifiedAwPath, modId) ?: return null

        val parsed = runCatching { AccessWidenerParser.parse(candidate) }.getOrNull()
        if (parsed == null || parsed.entries.isEmpty()) {
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

        if (commonProject != null) {
            val commonAw = commonProject.file("src/main/resources/$modId.accesswidener")
            if (commonAw.exists()) return commonAw
        }

        val localAw = project.file("src/main/resources/$modId.accesswidener")
        if (localAw.exists()) return localAw

        return null
    }

    fun generateAccessTransformer(project: Project, awFile: File, targetName: String): File {
        val parsed = AccessWidenerParser.parse(awFile)
        if (parsed.namespace != "named") {
            project.logger.warn("Prism: Access widener '${awFile.name}' uses namespace '${parsed.namespace}' instead of 'named' (Mojang). The generated access transformer for $targetName may contain incorrect names.")
        }
        val outputDir = project.layout.buildDirectory.dir("generated/prism/at").get().asFile
        val outputFile = File(outputDir, "${targetName}_accesstransformer.cfg")
        AccessWidenerConverter.writeAccessTransformer(parsed, outputFile)
        project.logger.lifecycle("Prism: Converted access widener '${awFile.name}' -> '${outputFile.name}' for $targetName (${parsed.entries.size} entries)")

        project.tasks.withType(ProcessResources::class.java).configureEach { task ->
            task.from(outputFile) { copy ->
                copy.into("META-INF")
                copy.rename { "accesstransformer.cfg" }
            }
        }

        return outputFile
    }
}
