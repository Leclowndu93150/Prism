package dev.prism.gradle.internal

import dev.prism.gradle.dsl.FabricConfiguration
import dev.prism.gradle.dsl.MetadataExtension
import dev.prism.gradle.dsl.RepositoryEntry
import dev.prism.gradle.dsl.VersionConfiguration
import net.fabricmc.loom.api.LoomGradleExtensionAPI
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.jvm.toolchain.JavaLanguageVersion

object FabricConfigurator {

    private fun isUnobfuscated(mcVersion: String): Boolean {
        val parts = mcVersion.split(".")
        val major = parts.getOrNull(0)?.toIntOrNull() ?: 1
        val minor = parts.getOrNull(1)?.toIntOrNull() ?: 0
        val patch = parts.getOrNull(2)?.toIntOrNull() ?: 0
        if (major > 1) return true
        if (minor > 21) return true
        if (minor == 21 && patch >= 11) return true
        return false
    }

    fun configure(
        loaderProject: Project,
        commonProject: Project,
        versionConfig: VersionConfiguration,
        fabricConfig: FabricConfiguration,
        metadata: MetadataExtension,
        extraRepositories: List<RepositoryEntry> = emptyList(),
    ) {
        loaderProject.pluginManager.apply("java-library")

        val unobfuscated = isUnobfuscated(versionConfig.minecraftVersion)

        if (unobfuscated) {
            loaderProject.extensions.extraProperties.set("fabric.loom.disableObfuscation", "true")
        }

        loaderProject.pluginManager.apply("fabric-loom")

        RepositorySetup.configure(loaderProject, extraRepositories)

        loaderProject.extensions.configure(JavaPluginExtension::class.java) { java ->
            java.toolchain.languageVersion.set(
                JavaLanguageVersion.of(versionConfig.resolvedJavaVersion)
            )
            java.withSourcesJar()
        }

        val loom = loaderProject.extensions.getByType(LoomGradleExtensionAPI::class.java)

        loaderProject.dependencies.add("minecraft", "com.mojang:minecraft:${versionConfig.minecraftVersion}")

        if (!unobfuscated) {
            if (fabricConfig.yarnVersion != null) {
                loaderProject.dependencies.add(
                    "mappings",
                    "net.fabricmc:yarn:${fabricConfig.yarnVersion}:v2"
                )
            } else {
                val mappingsDep = loom.layered { layered ->
                    layered.officialMojangMappings()
                    if (versionConfig.parchmentMinecraftVersion != null && versionConfig.parchmentMappingsVersion != null) {
                        layered.parchment(
                            "org.parchmentmc.data:parchment-${versionConfig.parchmentMinecraftVersion}:${versionConfig.parchmentMappingsVersion}@zip"
                        )
                    }
                }
                loaderProject.dependencies.add("mappings", mappingsDep)
            }
        }

        val depConfig = if (unobfuscated) "implementation" else "modImplementation"

        loaderProject.dependencies.add(
            depConfig,
            "net.fabricmc:fabric-loader:${fabricConfig.loaderVersion}"
        )

        fabricConfig.apiVersion?.let { apiVersion ->
            loaderProject.dependencies.add(
                depConfig,
                "net.fabricmc.fabric-api:fabric-api:$apiVersion"
            )
        }

        val commonAw = commonProject.file("src/main/resources/${metadata.modId}.accesswidener")
        val loaderAw = loaderProject.file("src/main/resources/${metadata.modId}.accesswidener")
        val aw = when {
            loaderAw.exists() -> loaderAw
            commonAw.exists() -> commonAw
            else -> null
        }
        if (aw != null) {
            loom.accessWidenerPath.set(aw)
        }

        if (!unobfuscated) {
            loom.mixin { mixin ->
                mixin.defaultRefmapName.set("${metadata.modId}.refmap.json")
            }
        }

        loom.runs { runs ->
            runs.getByName("client") { run ->
                run.setConfigName("Fabric Client (${versionConfig.minecraftVersion})")
                run.ideConfigGenerated(true)
                run.runDir("runs/${versionConfig.minecraftVersion}/fabric/client")
            }
            runs.getByName("server") { run ->
                run.setConfigName("Fabric Server (${versionConfig.minecraftVersion})")
                run.ideConfigGenerated(true)
                run.runDir("runs/${versionConfig.minecraftVersion}/fabric/server")
            }

            if (fabricConfig.apiVersion != null && fabricConfig.enableDatagen) {
                runs.create("datagen") { run ->
                    run.client()
                    run.setConfigName("Fabric Datagen (${versionConfig.minecraftVersion})")
                    run.ideConfigGenerated(true)
                    run.runDir("runs/${versionConfig.minecraftVersion}/fabric/datagen")
                    run.vmArg("-Dfabric-api.datagen")
                    run.vmArg("-Dfabric-api.datagen.output-dir=${loaderProject.file("src/main/generated").absolutePath}")
                    run.vmArg("-Dfabric-api.datagen.modid=${metadata.modId}")
                }

                loaderProject.extensions.configure(JavaPluginExtension::class.java) { java ->
                    java.sourceSets.getByName("main").resources.srcDir("src/main/generated")
                }
            }
        }

        RunApplicator.applyFabricRuns(loaderProject, fabricConfig.extraRuns, versionConfig, loom)

        JarNaming.configure(loaderProject, metadata, versionConfig, fabricConfig)
        CommonLoaderWiring.wire(loaderProject, commonProject, metadata)
        TemplateExpansion.configure(loaderProject, versionConfig, metadata)
    }
}
