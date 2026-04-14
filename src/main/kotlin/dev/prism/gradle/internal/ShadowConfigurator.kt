package dev.prism.gradle.internal

import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import dev.prism.gradle.dsl.DependencyBlock
import net.neoforged.moddevgradle.legacyforge.dsl.ObfuscationExtension
import org.gradle.api.Project
import org.gradle.api.file.DuplicatesStrategy
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.tasks.SourceSet
import org.gradle.jvm.tasks.Jar

object ShadowConfigurator {
    private const val SHADOW_MARKER = "dev.prism.shadowConfigured"

    fun configure(project: Project, relocation: DependencyBlock.ShadowRelocation) {
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
            task.exclude("META-INF/LICENSE*", "META-INF/NOTICE*", "LICENSE*", "NOTICE*")
            task.enableAutoRelocation.set(relocation.enabled)
            relocation.prefix?.let(task.relocationPrefix::set)
            task.addMultiReleaseAttribute.set(false)
            task.mergeServiceFiles()
            task.doFirst {
                // Shaded mod jars should not advertise external Class-Path entries.
                task.manifest.attributes.remove("Class-Path")
            }
        }

        project.pluginManager.withPlugin("net.neoforged.moddev.legacyforge") {
            val obfuscation = project.extensions.findByType(ObfuscationExtension::class.java)
                ?: return@withPlugin
            val mainSourceSet = project.extensions.getByType(JavaPluginExtension::class.java)
                .sourceSets.getByName(SourceSet.MAIN_SOURCE_SET_NAME)

            val reobfShadowJar = obfuscation.reobfuscate(shadowJar, mainSourceSet)
            project.tasks.named("assemble").configure { it.dependsOn(reobfShadowJar) }
        }
    }
}
