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
        sharedProject: Project? = null,
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
        CommonLoaderWiring.wire(loaderProject, commonProject, metadata, sharedProject)
        TemplateExpansion.configure(loaderProject, versionConfig, metadata)
    }

    fun configureSingle(
        project: Project,
        versionConfig: VersionConfiguration,
        forgeConfig: ForgeConfiguration,
        metadata: MetadataExtension,
        extraRepositories: List<RepositoryEntry> = emptyList(),
        sharedProject: Project? = null,
    ) {
        project.pluginManager.apply("java-library")
        project.pluginManager.apply("net.neoforged.moddev.legacyforge")

        RepositorySetup.configure(project, extraRepositories)

        project.extensions.configure(JavaPluginExtension::class.java) { java ->
            java.toolchain.languageVersion.set(JavaLanguageVersion.of(versionConfig.resolvedJavaVersion))
            java.withSourcesJar()
        }

        project.extensions.configure(LegacyForgeExtension::class.java) { legacyForge ->
            legacyForge.version = "${versionConfig.minecraftVersion}-${forgeConfig.loaderVersion}"
            legacyForge.validateAccessTransformers.set(true)

            if (versionConfig.parchmentMinecraftVersion != null && versionConfig.parchmentMappingsVersion != null) {
                legacyForge.parchment { p ->
                    p.minecraftVersion.set(versionConfig.parchmentMinecraftVersion)
                    p.mappingsVersion.set(versionConfig.parchmentMappingsVersion)
                }
            }

            val at = project.file("src/main/resources/META-INF/accesstransformer.cfg")
            if (at.exists()) { legacyForge.accessTransformers.from(at.absolutePath) }

            legacyForge.runs { runs ->
                runs.configureEach { run ->
                    run.systemProperty("forge.logging.markers", "REGISTRIES")
                    run.systemProperty("forge.logging.console.level", "debug")
                    run.ideName.set("Forge ${run.name.replaceFirstChar { it.uppercase() }} (${versionConfig.minecraftVersion})")
                }
                runs.create("client") { run ->
                    run.client()
                    run.gameDirectory.set(project.file("runs/client"))
                }
                runs.create("server") { run ->
                    run.server()
                    run.gameDirectory.set(project.file("runs/server"))
                }
                runs.create("data") { run ->
                    run.data()
                    run.gameDirectory.set(project.file("runs/data"))
                    run.programArguments.addAll("--mod", metadata.modId, "--all",
                        "--output", project.file("src/generated/resources/").absolutePath,
                        "--existing", project.file("src/main/resources/").absolutePath)
                }
            }

            legacyForge.mods { mods ->
                mods.create(metadata.modId) { mod ->
                    mod.sourceSet(project.extensions.getByType(JavaPluginExtension::class.java).sourceSets.getByName("main"))
                }
            }

            RunApplicator.applyMdgRuns(project, forgeConfig.extraRuns, versionConfig, "forge", legacyForge.runs)
        }

        project.extensions.configure(JavaPluginExtension::class.java) { java ->
            java.sourceSets.getByName("main").resources.srcDir("src/generated/resources")
        }

        JarNaming.configure(project, metadata, versionConfig, forgeConfig)
        TemplateExpansion.configure(project, versionConfig, metadata)
    }
}
