package dev.prism.gradle.internal

import dev.prism.gradle.dsl.DependencyBlock
import org.gradle.api.Project
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

object NativeJarRepackager {
    private val NATIVE_SUFFIXES = listOf(".so", ".dll", ".dylib", ".jnilib")

    /**
     * Resolves [dependency] to its single artifact jar and writes a copy with entries excluded
     * per [config]. Returns the repackaged file, or null if resolution produced no jar.
     */
    fun repackage(project: Project, dependency: String, config: DependencyBlock.JarJarConfig): File? {
        val detached = project.configurations.detachedConfiguration(
            project.dependencies.create(dependency)
        )
        detached.isTransitive = false
        val source = detached.resolve().firstOrNull { it.name.endsWith(".jar") } ?: return null

        val outputDir = File(project.layout.buildDirectory.asFile.get(), "prism/jarjar")
        outputDir.mkdirs()
        val output = File(outputDir, source.name)

        ZipFile(source).use { zip ->
            ZipOutputStream(output.outputStream().buffered()).use { out ->
                val entries = zip.entries()
                while (entries.hasMoreElements()) {
                    val entry = entries.nextElement()
                    if (shouldExclude(entry.name, config)) continue
                    out.putNextEntry(ZipEntry(entry.name))
                    if (!entry.isDirectory) {
                        zip.getInputStream(entry).use { it.copyTo(out) }
                    }
                    out.closeEntry()
                }
            }
        }
        return output
    }

    private fun shouldExclude(name: String, config: DependencyBlock.JarJarConfig): Boolean {
        if (config.excludedPaths.any { name == it || name.endsWith(it) }) {
            return true
        }
        if (config.excludedNativeTokens.isEmpty()) return false
        val isNative = NATIVE_SUFFIXES.any { name.endsWith(it) }
        if (!isNative) return false
        return config.excludedNativeTokens.any { name.contains(it) }
    }
}
