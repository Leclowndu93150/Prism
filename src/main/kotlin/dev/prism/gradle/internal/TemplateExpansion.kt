package dev.prism.gradle.internal

import dev.prism.gradle.dsl.MetadataExtension
import dev.prism.gradle.dsl.VersionConfiguration
import org.gradle.api.Project
import org.gradle.language.jvm.tasks.ProcessResources

object TemplateExpansion {
    fun configure(
        project: Project,
        versionConfig: VersionConfiguration,
        metadata: MetadataExtension,
    ) {
        project.tasks.named("processResources", ProcessResources::class.java) { task ->
            val expandProps = buildMap {
                put("version", metadata.version.ifEmpty { project.rootProject.version.toString() })
                put("group", metadata.group.ifEmpty { project.rootProject.group.toString() })
                put("minecraft_version", versionConfig.minecraftVersion)
                put("mod_name", metadata.name)
                put("mod_author", metadata.authors.joinToString(", "))
                put("mod_id", metadata.modId)
                put("license", metadata.license)
                put("description", metadata.description)
                put("java_version", versionConfig.resolvedJavaVersion.toString())
                put("credits", metadata.credits.joinToString(", "))

                versionConfig.fabricConfig?.let { fabric ->
                    put("fabric_loader_version", fabric.loaderVersion)
                    fabric.apiVersion?.let { put("fabric_version", it) }
                }

                versionConfig.neoForgeConfig?.let { neoforge ->
                    put("neoforge_version", neoforge.loaderVersion)
                    neoforge.loaderVersionRange?.let { put("neoforge_loader_version_range", it) }
                }

                versionConfig.forgeConfig?.let { forge ->
                    put("forge_version", forge.loaderVersion)
                    forge.loaderVersionRange?.let { put("forge_loader_version_range", it) }
                }

                versionConfig.lexForgeConfig?.let { lexForge ->
                    put("lexforge_version", lexForge.loaderVersion)
                    lexForge.loaderVersionRange?.let { put("lexforge_loader_version_range", it) }
                    put("forge_version", lexForge.loaderVersion)
                    lexForge.loaderVersionRange?.let { put("forge_loader_version_range", it) }
                }
            }

            val jsonExpandProps = expandProps.mapValues { (_, value) ->
                value.replace("\n", "\\\\n")
            }

            task.filesMatching(listOf("META-INF/mods.toml", "META-INF/neoforge.mods.toml")) {
                it.expand(expandProps)
            }

            task.filesMatching(listOf("pack.mcmeta", "fabric.mod.json", "*.mixins.json")) {
                it.expand(jsonExpandProps)
            }

            task.inputs.properties(expandProps)
        }
    }
}
