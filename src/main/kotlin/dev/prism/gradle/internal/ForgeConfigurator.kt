package dev.prism.gradle.internal

import dev.prism.gradle.dsl.ForgeConfiguration
import dev.prism.gradle.dsl.MetadataExtension
import dev.prism.gradle.dsl.RepositoryEntry
import dev.prism.gradle.dsl.VersionConfiguration
import net.neoforged.moddevgradle.legacyforge.dsl.LegacyForgeExtension
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.jvm.toolchain.JavaLanguageVersion

object ForgeConfigurator {
    fun configure(
        loaderProject: Project,
        commonProject: Project,
        versionConfig: VersionConfiguration,
        forgeConfig: ForgeConfiguration,
        metadata: MetadataExtension,
        extraRepositories: List<RepositoryEntry> = emptyList(),
    ) {
        loaderProject.pluginManager.apply("java-library")
        loaderProject.pluginManager.apply("net.neoforged.moddev.legacyforge")

        RepositorySetup.configure(loaderProject, extraRepositories)

        loaderProject.extensions.configure(JavaPluginExtension::class.java) { java ->
            java.toolchain.languageVersion.set(
                JavaLanguageVersion.of(versionConfig.resolvedJavaVersion)
            )
            java.withSourcesJar()
        }

        loaderProject.extensions.configure(LegacyForgeExtension::class.java) { legacyForge ->
            legacyForge.version = "${versionConfig.minecraftVersion}-${forgeConfig.loaderVersion}"

            legacyForge.validateAccessTransformers.set(true)

            if (versionConfig.parchmentMinecraftVersion != null && versionConfig.parchmentMappingsVersion != null) {
                legacyForge.parchment { parchment ->
                    parchment.minecraftVersion.set(versionConfig.parchmentMinecraftVersion)
                    parchment.mappingsVersion.set(versionConfig.parchmentMappingsVersion)
                }
            }

            val at = commonProject.file("src/main/resources/META-INF/accesstransformer.cfg")
            if (at.exists()) {
                legacyForge.accessTransformers.from(at.absolutePath)
            }

            val loaderAt = loaderProject.file("src/main/resources/META-INF/accesstransformer.cfg")
            if (loaderAt.exists()) {
                legacyForge.accessTransformers.from(loaderAt.absolutePath)
            }

            legacyForge.runs { runs ->
                runs.configureEach { run ->
                    run.systemProperty("forge.logging.markers", "REGISTRIES")
                    run.systemProperty("forge.logging.console.level", "debug")
                    run.ideName.set("Forge ${run.name.replaceFirstChar { it.uppercase() }} (${versionConfig.minecraftVersion})")
                }
                runs.create("client") { run ->
                    run.client()
                    run.gameDirectory.set(loaderProject.file("runs/${versionConfig.minecraftVersion}/forge/client"))
                }
                runs.create("server") { run ->
                    run.server()
                    run.gameDirectory.set(loaderProject.file("runs/${versionConfig.minecraftVersion}/forge/server"))
                }
                runs.create("data") { run ->
                    run.data()
                    run.gameDirectory.set(loaderProject.file("runs/${versionConfig.minecraftVersion}/forge/data"))
                    run.programArguments.addAll(
                        "--mod", metadata.modId,
                        "--all",
                        "--output", loaderProject.file("src/generated/resources/").absolutePath,
                        "--existing", loaderProject.file("src/main/resources/").absolutePath
                    )
                }
            }

            legacyForge.mods { mods ->
                mods.create(metadata.modId) { mod ->
                    mod.sourceSet(
                        loaderProject.extensions.getByType(JavaPluginExtension::class.java)
                            .sourceSets.getByName("main")
                    )
                }
            }
        }

        loaderProject.extensions.configure(JavaPluginExtension::class.java) { java ->
            java.sourceSets.getByName("main").resources.srcDir("src/generated/resources")
        }

        loaderProject.extensions.configure(LegacyForgeExtension::class.java) { legacyForge ->
            RunApplicator.applyMdgRuns(loaderProject, forgeConfig.extraRuns, versionConfig, "forge", legacyForge.runs)
        }

        JarNaming.configure(loaderProject, metadata, versionConfig, forgeConfig)
        CommonLoaderWiring.wire(loaderProject, commonProject, metadata)
        TemplateExpansion.configure(loaderProject, versionConfig, metadata)
    }
}
