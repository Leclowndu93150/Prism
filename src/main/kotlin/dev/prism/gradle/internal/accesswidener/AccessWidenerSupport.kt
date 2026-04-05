package dev.prism.gradle.internal.accesswidener

import org.gradle.api.Project
import java.io.File

object AccessWidenerSupport {

    fun resolveAccessWidener(
        project: Project,
        commonProject: Project?,
        unifiedAwPath: String?,
        modId: String
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
        val outputDir = project.layout.buildDirectory.dir("generated/prism/at").get().asFile
        val outputFile = File(outputDir, "${targetName}_accesstransformer.cfg")
        AccessWidenerConverter.writeAccessTransformer(parsed, outputFile)
        project.logger.lifecycle("Prism: Converted access widener '${awFile.name}' -> '${outputFile.name}' for ${targetName} (${parsed.entries.size} entries)")
        return outputFile
    }
}
