package dev.prism.gradle.internal

import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import dev.prism.gradle.dsl.DependencyBlock
import net.neoforged.moddevgradle.legacyforge.dsl.ObfuscationExtension
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.file.DuplicatesStrategy
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.TaskProvider
import org.gradle.jvm.tasks.Jar
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.jar.Attributes
import java.util.jar.JarFile
import java.util.jar.Manifest
import java.util.jar.JarOutputStream

object ShadowConfigurator {
    private const val SHADOW_MARKER = "dev.prism.shadowConfigured"

    internal fun configure(project: Project, settings: DependencyBlock.ShadowSettings) {
        val extras = project.extensions.extraProperties
        if (extras.has(SHADOW_MARKER)) return
        extras.set(SHADOW_MARKER, true)

        project.pluginManager.apply("com.gradleup.shadow")

        val shadowConfig = project.configurations.getByName("shadow")
        val shadowJar = project.tasks.named("shadowJar", ShadowJar::class.java)

        project.tasks.named("jar", Jar::class.java).configure { jar ->
            if (jar.archiveClassifier.orNull.isNullOrBlank()) {
                jar.archiveClassifier.set("dev")
            }
        }

        shadowJar.configure { task ->
            task.archiveClassifier.set("")
            task.configurations.set(listOf(shadowConfig))
            task.duplicatesStrategy = DuplicatesStrategy.EXCLUDE
            task.exclude("META-INF/LICENSE*", "META-INF/NOTICE*", "LICENSE*", "NOTICE*", *settings.taskExcludes.toTypedArray())
            for (relocation in settings.relocations) {
                task.relocate(relocation.pattern, relocation.destination) { relocator ->
                    relocation.includes.forEach(relocator::include)
                    relocation.excludes.forEach(relocator::exclude)
                }
            }
            if (settings.mergeServiceFileRoots.isEmpty()) {
                task.mergeServiceFiles()
            } else {
                settings.mergeServiceFileRoots.forEach { root ->
                    if (root == null) task.mergeServiceFiles() else task.mergeServiceFiles(root)
                }
            }
            task.doFirst {
                if (settings.enabled) {
                    val prefix = settings.prefix ?: "shadow"
                    dependencyPackages(task).forEach { pkg ->
                        task.relocate(pkg, "$prefix.$pkg") { relocator ->
                            settings.includes.forEach(relocator::include)
                            settings.excludes.forEach(relocator::exclude)
                        }
                    }
                }
                settings.manifestAttributesToRemove.forEach(task.manifest.attributes::remove)
            }
            settings.rawActions.forEach { it.execute(task) }
        }
        sanitizeTaskOutputs(shadowJar, settings)

        project.pluginManager.withPlugin("net.neoforged.moddev.legacyforge") {
            val obfuscation = project.extensions.findByType(ObfuscationExtension::class.java)
                ?: return@withPlugin
            val mainSourceSet = project.extensions.getByType(JavaPluginExtension::class.java)
                .sourceSets.getByName(SourceSet.MAIN_SOURCE_SET_NAME)

            val reobfShadowJar = obfuscation.reobfuscate(shadowJar, mainSourceSet)
            project.tasks.named("assemble").configure { it.dependsOn(reobfShadowJar) }
            sanitizeTaskOutputs(reobfShadowJar, settings)
        }
    }

    private fun sanitizeTaskOutputs(taskProvider: TaskProvider<out Task>, settings: DependencyBlock.ShadowSettings) {
        taskProvider.configure { task ->
            task.doLast {
                task.outputs.files.files
                    .filter { it.isFile && it.extension == "jar" }
                    .forEach { jar ->
                        stripEntries(jar.toPath(), settings.stripPatterns, settings.manifestAttributesToRemove)
                    }
            }
        }
    }

    private fun dependencyPackages(task: ShadowJar): List<String> {
        return task.includedDependencies.files
            .filter { it.isFile && it.extension == "jar" }
            .flatMap { jar ->
                JarFile(jar).use { input ->
                    input.entries().toList()
                        .asSequence()
                        .filter { !it.isDirectory && it.name.endsWith(".class") && it.name != "module-info.class" }
                        .map { it.name.substringBeforeLast('/').replace('/', '.') }
                        .filter { it.isNotBlank() }
                        .toList()
                }
            }
            .distinct()
            .sorted()
    }

    private fun stripEntries(
        jarPath: java.nio.file.Path,
        extraPatterns: List<String>,
        manifestAttributesToRemove: List<String>,
    ) {
        val temp = Files.createTempFile(jarPath.parent, jarPath.fileName.toString(), ".tmp")
        val extraMatchers = extraPatterns.map { java.nio.file.FileSystems.getDefault().getPathMatcher("glob:$it") }
        JarFile(jarPath.toFile()).use { input ->
            val manifest = input.manifest?.let { sanitizeManifest(it, manifestAttributesToRemove) }
            val outputStream = Files.newOutputStream(temp)
            val output = if (manifest != null) JarOutputStream(outputStream, manifest) else JarOutputStream(outputStream)
            output.use {
                val entries = input.entries()
                while (entries.hasMoreElements()) {
                    val entry = entries.nextElement()
                    val name = entry.name
                    if (name == JarFile.MANIFEST_NAME) {
                        continue
                    }
                    val matchesExtra = extraMatchers.any { it.matches(java.nio.file.Paths.get(name)) }
                    val isBrokenServiceDir = name.startsWith("META-INF/services/") &&
                        (entry.isDirectory || name == "META-INF/services/org/jline/terminal/provider/exec")
                    if (isBrokenServiceDir || matchesExtra) {
                        continue
                    }

                    val newEntry = java.util.jar.JarEntry(name).apply {
                        time = entry.time
                        comment = entry.comment
                        extra = entry.extra
                        method = entry.method
                        if (entry.method == java.util.zip.ZipEntry.STORED) {
                            size = entry.size
                            crc = entry.crc
                            compressedSize = entry.compressedSize
                        }
                    }
                    output.putNextEntry(newEntry)
                    if (!entry.isDirectory) {
                        input.getInputStream(entry).use { it.copyTo(output) }
                    }
                    output.closeEntry()
                }
            }
        }
        Files.move(temp, jarPath, StandardCopyOption.REPLACE_EXISTING)
    }

    private fun sanitizeManifest(source: Manifest, attributesToRemove: List<String>): Manifest {
        val manifest = Manifest(source)
        attributesToRemove.forEach { name ->
            manifest.mainAttributes.remove(Attributes.Name(name))
        }
        return manifest
    }
}
